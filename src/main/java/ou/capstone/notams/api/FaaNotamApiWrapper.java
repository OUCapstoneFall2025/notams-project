package ou.capstone.notams.api;

import ou.capstone.notams.exceptions.RateLimitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ou.capstone.notams.exceptions.NotamException;

/**
 * Utility class for connecting to the FAA NOTAM API.
 * Provides reusable methods for fetching raw NOTAM JSON data with support for both
 * ICAO location-based and coordinate-based queries.
 * <p>
 * To run with verbose logging use -DFaaNotamApiWrapper.VerboseLogging=true
 */
public final class FaaNotamApiWrapper
{
    private static final Logger logger = LoggerFactory.getLogger(FaaNotamApiWrapper.class);

    private static final String FAA_DOMAIN = "external-api.faa.gov";
    private static final String NOTAM_API_PATH = "/notamapi/v1/notams";
    private static final String RESPONSE_FORMAT = "geoJson";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_NUM = 1;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RADIUS_NM = 50;

    private static final boolean VERBOSE_LOGGING_ENABLED = System.getProperty(
                    "FaaNotamApiWrapper.VerboseLogging", "false" )
            .equalsIgnoreCase( "true" );

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
     * Query parameters builder for FAA NOTAM API requests.
     * Supports both ICAO location-based and coordinate-based queries.
     * Use constructors to create instances with required parameters.
     */
    public static final class QueryParamsBuilder {
        private final String icaoLocation;
        private final Double latitude;
        private final Double longitude;
        private final Integer radiusNm;
        private int pageSize = DEFAULT_PAGE_SIZE;
        private int pageNum = DEFAULT_PAGE_NUM;
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
        public QueryParamsBuilder pageSize(final int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Sets the page number for pagination.
         *
         * @param pageNum The page number (default: "1")
         * @return this builder for method chaining
         */
        public QueryParamsBuilder pageNum(final int pageNum) {
            this.pageNum = pageNum;
            return this;
        }

        /**
         * Builds the query string from the parameters.
         *
         * @return The URL-encoded query string
         */
        public String build() {
            final Map<String,Object> params = new HashMap<>();

            params.put( "responseFormat", RESPONSE_FORMAT );

            if (icaoLocation != null) {
                logger.debug("Building ICAO-based query for location: {}", icaoLocation);
                params.put( "icaoLocation", icaoLocation );
            } else {
                logger.debug("Building coordinate-based query: lat={}, lon={}, radius={} nm",
                        latitude, longitude, radiusNm);
                params.put( "locationLatitude", latitude );
                params.put( "locationLongitude", longitude );
                params.put( "locationRadius", radiusNm );
            }
            params.put( "classification", "DOM" );
            params.put( "pageSize", pageSize );
            params.put( "pageNum", pageNum );
            params.put( "sortBy", sortBy );
            params.put( "sortOrder", sortOrder );

            final String queryString = params.entrySet().stream()
                    .map( e -> String.format( "%s=%s",
                            URLEncoder.encode( e.getKey(),
                                    StandardCharsets.UTF_8 ),
                            URLEncoder.encode( String.valueOf( e.getValue() ),
                                    StandardCharsets.UTF_8 ) ) )
                    .collect( Collectors.joining( "&" ) );
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

        @Override
        public boolean equals( Object o )
        {
            if( o == null || getClass() != o.getClass() ) {
                return false;
            }
            QueryParamsBuilder that = (QueryParamsBuilder) o;
            return Objects.equals( icaoLocation, that.icaoLocation )
                    && Objects.equals( latitude, that.latitude )
                    && Objects.equals( longitude, that.longitude )
                    && Objects.equals( radiusNm, that.radiusNm )
                    && Objects.equals( pageSize, that.pageSize )
                    && Objects.equals( pageNum, that.pageNum );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( icaoLocation, latitude, longitude, radiusNm,
                    pageSize, pageNum, sortBy, sortOrder );
        }
    }

    /**
     * Fetches raw NOTAM JSON data from the FAA API using the specified query parameters.
     * This is a reusable method that supports both ICAO and coordinate-based queries.
     *
     * @param queryParams The query parameters builder
     * @param timeoutSeconds Optional timeout in seconds (defaults to 30 if null)
     * @return The raw JSON response as a String
     */
    protected static String fetchRawJson( final QueryParamsBuilder queryParams,
                                          final int timeoutSeconds )
            throws NotamException
    {
        logger.debug("Fetching NOTAMs with query parameters: {}", queryParams);

        validateCredentials();

        final String clientId = System.getenv("FAA_CLIENT_ID");
        final String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        final String queryString = queryParams.build();
        final URI uri;
        try {
            uri = new URI("https", FAA_DOMAIN, NOTAM_API_PATH, queryString, null);
        }
        catch( final URISyntaxException e ) {
            logger.error( "Bad syntax in URL? ", e);
            throw new NotamException(e);
        }

        // Verbose debug logging (only if enabled via system property)
        if (VERBOSE_LOGGING_ENABLED) {
            logger.debug("=== FAA API Request Details ===");
            logger.debug("Full URL: {}", uri);
            logger.debug("Client ID: {}...", clientId != null && clientId.length() > 4 ? clientId.substring(0, 4) : "****");
            logger.debug("Client Secret: [REDACTED - {} characters]", clientSecret != null ? clientSecret.length() : 0);
            logger.debug("Timeout: {} seconds", timeoutSeconds != -1 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS);
            logger.debug("===============================");
        } else {
            logger.debug("Requesting URL: {}", uri);
        }

        final int timeout = timeoutSeconds != -1 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        final HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .timeout(Duration.ofSeconds(timeout))
                .build();

        final HttpResponse<String> response;
        try (final HttpClient http = HttpClient.newHttpClient()) {
            response = http.send( request, BodyHandlers.ofString() );
        }
        catch( final IOException e ) {
            logger.error( "Could not make HTTP request to API", e );
            throw new NotamException( e );
        }
        catch( final InterruptedException e ) {
            logger.error( "Interrupted while making HTTP request to API", e );
            throw new NotamException( e );
        }

        logger.info("FAA API response status: {}", response.statusCode());

        if (response.statusCode() == 200) {
            final String responseBody = response.body();

            // Verbose logging of response details (only if enabled)
            if (VERBOSE_LOGGING_ENABLED) {
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
        } else if (response.statusCode() == 429) {
            logger.warn("FAA API rate limit exceeded. URL: {}", uri);
            logger.debug("Rate limit response body: {}", response.body());
            throw new RateLimitException("FAA API rate limit has been exceeded. Please wait a few minutes and try again.");
        } else {
            logger.error("FAA API returned non-200 status: {}. URL: {}", response.statusCode(), uri);
            logger.error("Response body: {}", response.body());
            throw new NotamException("FAA API returned non-200 status: " + response.statusCode());
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
    protected static String fetchRawJson( final QueryParamsBuilder queryParams )
            throws NotamException
    {
        return fetchRawJson(queryParams, -1);
    }

    public static List<String> fetchAllPages( final QueryParamsBuilder queryParams )
            throws NotamException
    {
        return fetchAllPages( queryParams, -1 );
    }

    public static List<String> fetchAllPages( final QueryParamsBuilder queryParams,
                                              final int timeoutInSeconds )
            throws NotamException
    {
        final List<String> allPages = new ArrayList<>();

        final String firstResult = fetchRawJson( queryParams, timeoutInSeconds );
        final ObjectMapper mapper = new ObjectMapper();
        JsonNode root = null;
        try {
            root = mapper.readTree( firstResult );
        }
        catch( final JsonProcessingException e ) {
            logger.error( "Unable to process JSON response from API", e );
            throw new NotamException( e );
        }
        int currentPage = root.get( "pageNum" ).asInt();
        final int totalPages = root.get( "totalPages" ).asInt();
        allPages.add( firstResult );

        while( currentPage < totalPages ) {
            final String nextResult = fetchRawJson( queryParams.pageNum(
                     currentPage + 1 ), timeoutInSeconds );
            final JsonNode thisPageRoot;
            try {
                thisPageRoot = mapper.readTree( nextResult );
            }
            catch( final JsonProcessingException e ) {
                logger.error( "Unable to process JSON response from API", e );
                throw new NotamException( e );
            }
            currentPage = thisPageRoot.get( "pageNum" ).asInt();
            allPages.add( nextResult );
        }

        return allPages;
    }
}