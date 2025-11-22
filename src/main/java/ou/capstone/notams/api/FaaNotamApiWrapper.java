package ou.capstone.notams.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for connecting to the FAA NOTAM API.
 * Provides reusable methods for fetching raw NOTAM JSON data with support for both
 * ICAO location-based and coordinate-based queries.
 *
 * To run with verbose logging use -DConnectToApi.VerboseLogging=true
 */
public final class FaaNotamApiWrapper
{

    private static final Logger logger = LoggerFactory.getLogger(FaaNotamApiWrapper.class);

    private static final String FAA_DOMAIN = "external-api.faa.gov";
    private static final String NOTAM_API_PATH = "/notamapi/v1/notams";
    private static final String RESPONSE_FORMAT = "geoJson";
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final String DEFAULT_PAGE_NUM = "1";

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RADIUS_NM = 50;

    // System property to enable verbose debug logging
    private static final String VERBOSE_LOGGING_PROPERTY = "ConnectToApi.VerboseLogging";

    /**
     * Validates that FAA API credentials are available in environment variables or system properties.
     * Checks environment variables first, then falls back to system properties.
     *
     * @throws IllegalStateException if credentials are missing
     */
    public static void validateCredentials() {
        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        if (clientId == null || clientSecret == null) {
            logger.error("FAA API credentials not found in environment variables");
            throw new IllegalStateException("FAA_CLIENT_ID or FAA_CLIENT_SECRET not set in environment!");
        }
    }

    /**
     * Checks if verbose logging is enabled via system property.
     *
     * @return true if verbose logging is enabled
     */
    private static boolean isVerboseLoggingEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(VERBOSE_LOGGING_PROPERTY));
    }

    /**
     * Query parameters builder for FAA NOTAM API requests.
     * Supports both ICAO location-based and coordinate-based queries.
     * Use constructors to create instances with required parameters.
     */
    public static final class QueryParamsBuilder {
        private final String icaoLocation;
        private final Double latitude;
        private final Double longitude;
        private final Integer radiusNm;
        private String pageSize = DEFAULT_PAGE_SIZE;
        private String pageNum = DEFAULT_PAGE_NUM;
        private final String sortBy = "effectiveStartDate";
        private final String sortOrder = "Desc";

        /**
         * Creates a builder for ICAO location-based queries.
         *
         * @param icaoLocation The ICAO airport code (e.g., "KOKC")
         */
        public QueryParamsBuilder(final String icaoLocation) {
            this.icaoLocation = icaoLocation;
            this.latitude = null;
            this.longitude = null;
            this.radiusNm = null;
        }

        /**
         * Creates a builder for coordinate-based queries with default radius.
         *
         * @param latitude The latitude
         * @param longitude The longitude
         */
        public QueryParamsBuilder(final double latitude, final double longitude) {
            this.icaoLocation = null;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusNm = DEFAULT_RADIUS_NM;
        }

        /**
         * Creates a builder for coordinate-based queries with specified radius.
         *
         * @param latitude The latitude
         * @param longitude The longitude
         * @param radiusNm The radius in nautical miles
         */
        public QueryParamsBuilder(final double latitude, final double longitude, final int radiusNm) {
            this.icaoLocation = null;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusNm = radiusNm;
        }

        /**
         * Sets the page size for pagination.
         *
         * @param pageSize The page size (default: "50")
         * @return this builder for method chaining
         */
        public QueryParamsBuilder pageSize(final String pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Sets the page number for pagination.
         *
         * @param pageNum The page number (default: "1")
         * @return this builder for method chaining
         */
        public QueryParamsBuilder pageNum(final String pageNum) {
            this.pageNum = pageNum;
            return this;
        }

        /**
         * Builds the query string from the parameters.
         *
         * @return The URL-encoded query string
         */
        private String build() {
            final List<String> params = new ArrayList<>();

            params.add("responseFormat=" + enc(RESPONSE_FORMAT));

            if (icaoLocation != null) {
                logger.debug("Building ICAO-based query for location: {}", icaoLocation);
                params.add("icaoLocation=" + enc(icaoLocation));
            } else {
                logger.debug("Building coordinate-based query: lat={}, lon={}, radius={}nm",
                        latitude, longitude, radiusNm);
                params.add("locationLatitude=" + enc(String.valueOf(latitude)));
                params.add("locationLongitude=" + enc(String.valueOf(longitude)));
                params.add("locationRadius=" + enc(String.valueOf(radiusNm)));
            }
            params.add("classification=DOM");
            params.add("pageSize=" + enc(pageSize));
            params.add("pageNum=" + enc(pageNum));
            params.add("sortBy=" + enc(sortBy));
            params.add("sortOrder=" + enc(sortOrder));

            final String queryString = String.join("&", params);
            logger.debug("Built query string: {}", queryString);

            return queryString;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("QueryParamsBuilder{");
            if (icaoLocation != null) {
                sb.append("queryType=ICAO, icaoLocation='").append(icaoLocation).append('\'');
            } else {
                sb.append("queryType=COORDINATE")
                        .append(", latitude=").append(latitude)
                        .append(", longitude=").append(longitude)
                        .append(", radiusNm=").append(radiusNm);
            }
            sb.append(", pageSize='").append(pageSize).append('\'')
                    .append(", pageNum='").append(pageNum).append('\'')
                    .append(", sortBy='").append(sortBy).append('\'')
                    .append(", sortOrder='").append(sortOrder).append('\'')
                    .append('}');
            return sb.toString();
        }
    }

    /**
     * Fetches raw NOTAM JSON data from the FAA API using the specified query parameters.
     * This is a reusable method that supports both ICAO and coordinate-based queries.
     *
     * @param queryParams The query parameters builder
     * @param timeoutSeconds Optional timeout in seconds (defaults to 30 if null)
     * @return The raw JSON response as a String
     * @throws Exception if API call fails or credentials are missing
     */
    public static String fetchRawJson(final QueryParamsBuilder queryParams, final Integer timeoutSeconds) throws Exception {
        logger.debug("Fetching NOTAMs with query parameters: {}", queryParams);

        // Check if we should use mock data (set via -DConnectToApi.UseMockData=true)
        final String useMockData = System.getProperty("ConnectToApi.UseMockData");
        if ("true".equalsIgnoreCase(useMockData)) {
            logger.info("Using mock data (ConnectToApi.UseMockData system property is set)");
            return loadMockJson();
        }

        // Validate credentials
        validateCredentials();

        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        final String queryString = queryParams.build();
        final URI uri = new URI("https", FAA_DOMAIN, NOTAM_API_PATH, queryString, null);

        // Verbose debug logging (only if enabled via system property)
        if (isVerboseLoggingEnabled()) {
            logger.debug("=== FAA API Request Details ===");
            logger.debug("Full URL: {}", uri);
            logger.debug("Client ID: {}...", clientId != null && clientId.length() > 4 ? clientId.substring(0, 4) : "****");
            logger.debug("Client Secret: [REDACTED - {} characters]", clientSecret != null ? clientSecret.length() : 0);
            logger.debug("Timeout: {} seconds", timeoutSeconds != null ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS);
            logger.debug("===============================");
        } else {
            logger.debug("Requesting URL: {}", uri);
        }

        final int timeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .timeout(Duration.ofSeconds(timeout))
                .build();

        final HttpClient http = HttpClient.newHttpClient();
        final HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

        logger.info("FAA API response status: {}", response.statusCode());

        if (response.statusCode() == 200) {
            final String responseBody = response.body();

            // Verbose logging of response details (only if enabled)
            if (isVerboseLoggingEnabled()) {
                logger.debug("=== FAA API Response Details ===");
                logger.debug("Response Body Length: {} characters", responseBody.length());

                // Log first 500 characters of response for debugging
                if (!responseBody.isEmpty()) {
                    final String preview = responseBody.length() > 500
                            ? responseBody.substring(0, 500) + "..."
                            : responseBody;
                    logger.debug("Response Body Preview:\n{}", preview);
                }
                logger.debug("================================");
            } else {
                logger.debug("Successfully fetched NOTAM JSON response ({} characters)", responseBody.length());
            }

            return responseBody;
        } else {
            logger.error("FAA API returned non-200 status: {}. URL: {}", response.statusCode(), uri);
            logger.error("Response body: {}", response.body());
            throw new IOException("FAA API returned non-200 status: " + response.statusCode());
        }
    }

    /**
     * Fetches raw NOTAM JSON data from the FAA API using the specified query parameters.
     * Uses default timeout.
     *
     * @param queryParams The query parameters builder
     * @return The raw JSON response as a String
     * @throws Exception if API call fails or credentials are missing
     */
    public static String fetchRawJson(final QueryParamsBuilder queryParams) throws Exception {
        return fetchRawJson(queryParams, null);
    }

    /**
     * Loads mock NOTAM JSON data from resources for testing/development purposes.
     *
     * @return The raw JSON string from the mock data file
     * @throws Exception if the mock data file cannot be read
     */
    private static String loadMockJson() throws Exception {
        logger.info("Loading mock NOTAM data from resources");

        try (final InputStream inputStream = FaaNotamApiWrapper.class.getClassLoader()
                .getResourceAsStream("mock-faa-response.json")) {

            if (inputStream == null) {
                throw new IOException("Mock data file 'mock-faa-response.json' not found in resources");
            }

            final String mockJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Successfully loaded mock NOTAM JSON");
            return mockJson;

        } catch (IOException e) {
            logger.error("Failed to load mock NOTAM data: {}", e.getMessage());
            throw new Exception("Failed to load mock NOTAM data", e);
        }
    }

    private static String enc(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}