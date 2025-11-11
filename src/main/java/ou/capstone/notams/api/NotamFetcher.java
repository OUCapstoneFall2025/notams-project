package ou.capstone.notams.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ou.capstone.notams.ConnectToAPI;
import ou.capstone.notams.route.Coordinate;
import ou.capstone.notams.route.RouteCalculator;
import ou.capstone.notams.validation.AirportDirectory;

/**
 * Fetches raw NOTAM data from the FAA API for given routes and airports.
 * CCS-16: Responsible for API connection and data retrieval along flight routes.
 * Credential validation is handled by ConnectToAPI when making API calls.
 *
 * CCS-61: Added lightweight profiling logs to identify where time is spent.
 */
public class NotamFetcher {

    private static final Logger logger = LoggerFactory.getLogger(NotamFetcher.class);

    private final AirportDirectory airportDirectory;

    // Radius in nautical miles for location queries
    private static final int QUERY_RADIUS_NM = 50;

    // Spacing between waypoints along route
    private static final double WAYPOINT_SPACING_NM = 50.0;

    // Fetches ten pages per wapoint
    private static final int MAX_PAGES_PER_WAYPOINT =
        Integer.parseInt(System.getenv().getOrDefault("NOTAM_MAX_PAGES_PER_WAYPOINT", "10"));

    // HTTP per-request timeout (seconds), configurable for experimentation
    private static final int HTTP_TIMEOUT_SECONDS =
            Integer.parseInt(System.getenv().getOrDefault("NOTAM_HTTP_TIMEOUT_SECONDS", "30"));

    // Toggleable via JVM property: -DVISUALIZE_ROUTE=true
    private static final boolean VISUALIZE_ROUTE = Boolean.getBoolean("VISUALIZE_ROUTE");

    /**
     * Constructs a NotamFetcher.
     * Credential validation is handled by ConnectToAPI when making API calls.
     */
    public NotamFetcher() {
        this.airportDirectory = new AirportDirectory();
    }

    /**
     * Fetch raw NOTAM JSON data for a flight route between two airports.
     * Queries along the great-circle route using waypoints.
     *
     * @param departureCode IATA or ICAO code of the departure airport
     * @param destinationCode IATA or ICAO code of the destination airport
     */
    public List<String> fetchForRoute(String departureCode, String destinationCode)
            throws Exception {

        final long overallStart = System.currentTimeMillis();

        // Measure airport lookup
        final long airportStart = System.currentTimeMillis();
        Coordinate depCoords = getAirportCoordinates(departureCode);
        Coordinate destCoords = getAirportCoordinates(destinationCode);
        final long airportEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Airport coordinate lookup took {} ms ({} -> {}, {} -> {})",
                    (airportEnd - airportStart), departureCode, depCoords, destinationCode, destCoords);
        }

        // Measure waypoint calculation
        final long waypointStart = System.currentTimeMillis();
        List<Coordinate> waypoints = RouteCalculator.getRouteWaypoints(
                depCoords.getLatitude(), depCoords.getLongitude(),
                destCoords.getLatitude(), destCoords.getLongitude(),
                WAYPOINT_SPACING_NM
        );
        final long waypointEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            final double approxPathLengthNm = Math.max(0, (waypoints.size() - 1) * WAYPOINT_SPACING_NM);
            logger.debug("Waypoint calculation took {} ms ({} waypoints, spacing {} nm, approx length ~{} nm)",
                    (waypointEnd - waypointStart), waypoints.size(), WAYPOINT_SPACING_NM, Math.round(approxPathLengthNm));
        }

        List<String> responses = new ArrayList<>();

        // Google Maps visualization toggleable via system properties
        final long visualizationStart = System.currentTimeMillis();
        printRouteVisualization(waypoints);
        final long visualizationEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Total visualization creation time across all waypoints: {} ms", visualizationEnd - visualizationStart);
        }

        // Measure total fetch time
        final long fetchStart = System.currentTimeMillis();

        int index = 1;
        for (Coordinate waypoint : waypoints) {
            final long singleFetchStart = System.currentTimeMillis();

            // Fetch ALL pages for the waypoint (adds to responses); keeps the single-page method intact.
            List<String> pages = fetchAll(waypoint.getLatitude(), waypoint.getLongitude(), QUERY_RADIUS_NM);
            responses.addAll(pages);

            final long singleFetchEnd = System.currentTimeMillis();
            if (logger.isDebugEnabled()) {
                logger.debug("Fetch {}/{} at ({}, {}) took {} ms",
                        index++, waypoints.size(),
                        waypoint.getLatitude(), waypoint.getLongitude(),
                        (singleFetchEnd - singleFetchStart));
            }
        }

        final long fetchEnd = System.currentTimeMillis();
        final long totalFetchTime = fetchEnd - fetchStart;
        if (logger.isDebugEnabled()) {
            logger.debug("Total API response time across all waypoints: {} ms", totalFetchTime);
        }

        final long overallEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Total fetchForRoute() time: {} ms", (overallEnd - overallStart));
        }

        return responses;
    }

    // ------------------------------------------------------------------
    // Pagination helpers using ConnectToAPI
    // ------------------------------------------------------------------

    /**
     * Fetch all NOTAM pages for a given airport code using the ConnectToAPI helper.
     */
    private List<String> fetchAll(final String airportCode) {
        ConnectToAPI.QueryParamsBuilder queryBuilder =
                new ConnectToAPI.QueryParamsBuilder(airportCode);
        queryBuilder.pageNum("1");
        return fetchAll(queryBuilder);
    }

    /**
     * Fetch all NOTAM pages for a given lat/lon/radius using the ConnectToAPI helper.
     */
    private List<String> fetchAll(double latitude, double longitude, int queryRadiusNm) {
        ConnectToAPI.QueryParamsBuilder queryBuilder =
                new ConnectToAPI.QueryParamsBuilder(latitude, longitude, queryRadiusNm);
        queryBuilder.pageNum("1");
        return fetchAll(queryBuilder);
    }

    /**
     * Core pagination logic: fetch page 1, inspect the JSON to see how many pages
     * exist, then loop pages 2..N using the same QueryParamsBuilder.
     */
    private List<String> fetchAll(final ConnectToAPI.QueryParamsBuilder queryBuilder) {
        List<String> pages = new ArrayList<>();

        try {
            // First page
            String result = ConnectToAPI.fetchRawJson(queryBuilder);
            pages.add(result);

            // Determine how many pages the API says we have
            int maxPagesInResult = extractTotalPages(result);
            // ADD safety limit
            maxPagesInResult = Math.min(maxPagesInResult, MAX_PAGES_PER_WAYPOINT);

            // If we can't find pagination info, just return the first page
            if (maxPagesInResult <= 1) {
                return pages;
            }

            // Fetch remaining pages: 2..maxPagesInResult
            for (int currentPage = 2; currentPage <= maxPagesInResult; currentPage++) {
                queryBuilder.pageNum(String.valueOf(currentPage));
                String nextPage = ConnectToAPI.fetchRawJson(queryBuilder);
                pages.add(nextPage);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching NOTAM pages", e);
        }

        return pages;
    }

    /**
     * Very lightweight JSON inspection to extract "totalPages" from the FAA response
     * without pulling in extra JSON libraries here.
     *
     * Expected shape somewhere in the response:
     *   "totalPages": 5
     *
     * If not found or unparsable, falls back to 1.
     */
    private static int extractTotalPages(String json) {
        if (json == null) {
            return 1;
        }
        final String key = "\"totalPages\":";
        int idx = json.indexOf(key);
        if (idx == -1) {
            return 1;
        }

        int i = idx + key.length();
        int len = json.length();

        // Skip whitespace
        while (i < len && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int start = i;

        // Read digits
        while (i < len && Character.isDigit(json.charAt(i))) {
            i++;
        }

        if (start == i) {
            return 1;
        }

        try {
            return Integer.parseInt(json.substring(start, i));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Prints a Google Maps visualization URL of the route if VISUALIZE_ROUTE is enabled.
     */
    private static void printRouteVisualization(List<Coordinate> waypoints) {
        if (!VISUALIZE_ROUTE || waypoints == null || waypoints.isEmpty()) {
            return;
        }

        logger.info("======= ROUTE VISUALIZATION =======");
        logger.info("Google Maps URL (copy and paste to view):");

        StringBuilder mapsUrl = new StringBuilder("https://www.google.com/maps/dir/");
        for (Coordinate waypoint : waypoints) {
            mapsUrl.append(waypoint.getLatitude())
                   .append(",")
                   .append(waypoint.getLongitude())
                   .append("/");
        }

        logger.info(mapsUrl.toString());
        logger.info("===================================\n");
    }

    /**
     * Fetches NOTAMs (Notice to Airmen) for a specified geographic location and radius.
     * Uses ConnectToAPI utility class to eliminate duplicate HTTP client code.
     * The response is returned in GeoJSON format.
     *
     * @param latitude   the latitude of the location to fetch NOTAMs for
     * @param longitude  the longitude of the location to fetch NOTAMs for
     * @param radiusNm   the radius (in nautical miles) around the location to search for NOTAMs
     * @return           the API response body as a String in GeoJSON format
     * @throws Exception if an error occurs during the API request or response handling
     */
    public String fetchForLocation(double latitude, double longitude, int radiusNm)
        throws Exception {

        final long t0 = System.currentTimeMillis();

        // Use ConnectToAPI utility class for reusable HTTP client code
        final List<String> pages = fetchAll(latitude, longitude, radiusNm);

        // For this method, return a single String. If there is more than one
        // page, simply concatenate them with newlines. Route-based callers
        // still use the List<String> from fetchForRoute(..).
        final String response;
        if (pages.isEmpty()) {
            response = "";
        } else if (pages.size() == 1) {
            response = pages.get(0);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String page : pages) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(page);
            }
            response = sb.toString();
        }

        final long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Single HTTP fetch took {} ms",
                    (t1 - t0));
        }

        return response;
    }

    /**
     * Retrieves the latitude and longitude coordinates for a given airport code.
     * Accepts both IATA (3-letter) and ICAO (4-letter) local codes.
     * Uses the AirportDirectory to look up coordinates from the airport CSV database.
     *
     * @param airportCode the code of the airport
     * @return an array containing latitude and longitude as [lat, lon]
     * @throws IllegalArgumentException if the airport code is not found
     */
    private Coordinate getAirportCoordinates(String airportCode) {
        Optional<Coordinate> coords = airportDirectory.getCoordinates(airportCode);
        if (coords.isEmpty()) {
            throw new IllegalArgumentException("No coordinates found for airport: " + airportCode);
        }
        return coords.get();
    }
}

