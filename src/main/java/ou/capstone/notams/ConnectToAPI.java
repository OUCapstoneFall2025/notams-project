package ou.capstone.notams;

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
 */
public final class ConnectToAPI {

    private static final Logger logger = LoggerFactory.getLogger(ConnectToAPI.class);

    private static final String FAA_DOMAIN = "external-api.faa.gov";
    private static final String NOTAM_API_PATH = "/notamapi/v1/notams";
    private static final String RESPONSE_FORMAT = "geoJson";
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final String DEFAULT_PAGE_NUM = "1";
    private static final String SORT_BY = "effectiveStartDate";
    private static final String SORT_ORDER = "Desc";

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RADIUS_NM = 50;

    /**
     * Validates that FAA API credentials are available in environment variables.
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
        private String sortBy = SORT_BY;
        private String sortOrder = SORT_ORDER;

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
         * Sets the sort field.
         * 
         * @param sortBy The field to sort by (default: "effectiveStartDate")
         * @return this builder for method chaining
         */
        public QueryParamsBuilder sortBy(final String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        /**
         * Sets the sort order.
         * 
         * @param sortOrder The sort order - "Asc" or "Desc" (default: "Desc")
         * @return this builder for method chaining
         */
        public QueryParamsBuilder sortOrder(final String sortOrder) {
            this.sortOrder = sortOrder;
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
                params.add("icaoLocation=" + enc(icaoLocation));
            } else {
                params.add("latitude=" + enc(String.valueOf(latitude)));
                params.add("longitude=" + enc(String.valueOf(longitude)));
                params.add("radius=" + enc(String.valueOf(radiusNm)));
            }
            
            params.add("pageSize=" + enc(pageSize));
            params.add("pageNum=" + enc(pageNum));
            params.add("sortBy=" + enc(sortBy));
            params.add("sortOrder=" + enc(sortOrder));
            
            return String.join("&", params);
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
        logger.debug("Fetching NOTAMs with query parameters");

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
        logger.debug("Requesting URL: {}", uri);

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
            logger.debug("Successfully fetched NOTAM JSON response");
            return response.body();
        } else {
            logger.error("FAA API returned non-200 status: {}. URL: {}", response.statusCode(), uri);
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
        
        try (final InputStream inputStream = ConnectToAPI.class.getClassLoader()
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
