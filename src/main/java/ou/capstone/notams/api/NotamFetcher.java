package ou.capstone.notams.api;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ou.capstone.notams.Notam;
import ou.capstone.notams.route.Coordinate;
import ou.capstone.notams.route.RouteCalculator;
import ou.capstone.notams.validation.AirportDirectory;
import ou.capstone.notams.exceptions.RateLimitException;

/**
 * Uses ConnectToAPI for all FAA NOTAM queries and converts JSON into NOTAM objects using NotamParser.
 * CCS-16: Responsible for API connection and data retrieval along flight routes.
 * Credential validation is handled by ConnectToAPI when making API calls.
 *
 * CCS-61: Added lightweight profiling logs to identify where time is spent.
 *
 * CCS-51: Refactor to integrate reusable API utility and parsing layer.
 */

public class NotamFetcher {

    private static final Logger logger = LoggerFactory.getLogger(NotamFetcher.class);

    private final AirportDirectory airportDirectory;
    private final List<ApiCredentials> apiCredentials;
    private final ExecutorService executor;

    // Radius in nautical miles for location queries
    private static final int QUERY_RADIUS_NM = 50;

    // Spacing between waypoints along route
    private static final double WAYPOINT_SPACING_NM = 50.0;

    // HTTP per-request timeout (seconds), configurable for experimentation
    private static final int HTTP_TIMEOUT_SECONDS =
            Integer.parseInt(System.getenv().getOrDefault("NOTAM_HTTP_TIMEOUT_SECONDS", "30"));

    // Toggleable via JVM property: -DVISUALIZE_ROUTE=true
    private static final boolean VISUALIZE_ROUTE = Boolean.getBoolean("VISUALIZE_ROUTE");

    private static final NotamParser parser = new NotamParser();

    /**
     * Constructs a NotamFetcher.
     * Loads API credentials from notam-config.txt
     */
    public NotamFetcher() {
        this.airportDirectory = new AirportDirectory();
        this.apiCredentials = loadApiCredentials();

        final int poolSize = (apiCredentials.size() >= 2) ? 4 : 2; // 2 keys -> 4 threads, 1 key -> 2 threads
        this.executor = Executors.newFixedThreadPool(poolSize);

        logger.info("Loaded {} API credential pair(s); using {} thread(s)",
                apiCredentials.size(), poolSize);
    }

    /**
     * Loads API credentials from notam-config.txt
     * Format: FAA_CLIENT_ID_1=xxx, FAA_CLIENT_SECRET_1=xxx, etc.
     */
    private List<ApiCredentials> loadApiCredentials() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("notam-config.txt"));
            Map<String, String> config = new HashMap<>();

            // Parse the config file
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }

            // Build credential pairs
            List<ApiCredentials> credentials = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {  // Support up to 10 pairs
                String clientId = config.get("FAA_CLIENT_ID_" + i);
                String clientSecret = config.get("FAA_CLIENT_SECRET_" + i);

                if (clientId != null && clientSecret != null && !clientId.isEmpty() && !clientSecret.isEmpty()) {
                    credentials.add(new ApiCredentials(clientId, clientSecret));
                    logger.info("Loaded credential pair #{}", i);
                }
            }

            if (credentials.isEmpty()) {
                throw new RuntimeException("No valid credential pairs found in notam-config.txt");
            }

            return credentials;

        } catch (Exception e) {
            throw new RuntimeException("Could not load notam-config.txt: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch list of NOTAMs for a flight route between two airports.
     * Queries along the great-circle route using waypoints.
     *
     * @param departureCode IATA or ICAO code of the departure airport
     * @param destinationCode IATA or ICAO code of the destination airport
     */
    public List<Notam> fetchForRoute(String departureCode, String destinationCode)
            throws Exception {

        final long overallStart = System.currentTimeMillis();

        // Airport lookup
        final long airportStart = System.currentTimeMillis();
        final Coordinate depCoords = getAirportCoordinates(departureCode);
        final Coordinate destCoords = getAirportCoordinates(destinationCode);
        final long airportEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Airport coordinate lookup took {} ms ({} -> {}, {} -> {})",
                    (airportEnd - airportStart), departureCode, depCoords, destinationCode, destCoords);
        }

        // Waypoint calculation
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

        // Google Maps visualization toggleable via system properties
        final long visualizationStart = System.currentTimeMillis();
        printRouteVisualization(waypoints);
        final long visualizationEnd = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Total visualization creation time across all waypoints: {} ms", visualizationEnd - visualizationStart);
        }

        // Fetch NOTAMs with credential rotation
        final long fetchStart = System.currentTimeMillis();

        final List<CompletableFuture<List<Notam>>> futures = new ArrayList<>();
        int idx = 0;

        for (Coordinate waypoint : waypoints) {
            final int waypointIndex = idx;
            final int credIdx;
            if (apiCredentials.size() >= 2) {
                credIdx = (idx / 2) % 2;
            } else {
                credIdx = 0;
            }

            final ApiCredentials credentials = apiCredentials.get(credIdx);
            final int credentialNumber = credIdx + 1;
            idx++;

            futures.add(
                    CompletableFuture.supplyAsync(() -> {
                        final long singleFetchStart = System.currentTimeMillis();
                        List<Notam> waypointNotams = Collections.emptyList();

                        try {
                            waypointNotams = fetchForLocation(
                                    waypoint.getLatitude(),
                                    waypoint.getLongitude(),
                                    QUERY_RADIUS_NM,
                                    credentials
                            );
                        } catch (RateLimitException e) {
                            logger.warn("Rate limit hit for credential pair #{} at waypoint {} ({}, {})",
                                    credentialNumber, waypointIndex + 1,
                                    waypoint.getLatitude(), waypoint.getLongitude());
                            throw new CompletionException(e);
                        } catch (Exception e) {
                            logger.warn("Skipping waypoint ({}, {}) due to error: {}",
                                    waypoint.getLatitude(), waypoint.getLongitude(), e.getMessage());
                            if (logger.isDebugEnabled()) {
                                logger.debug("Stack trace for failed waypoint:", e);
                            }
                        }

                        final long singleFetchEnd = System.currentTimeMillis();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Fetch {}/{} using credential #{} at ({}, {}) took {} ms ({} NOTAMs) [thread={}]",
                                    waypointIndex + 1, waypoints.size(), credentialNumber,
                                    waypoint.getLatitude(), waypoint.getLongitude(),
                                    (singleFetchEnd - singleFetchStart), waypointNotams.size(),
                                    Thread.currentThread().getName());
                        }

                        return waypointNotams;
                    }, executor)
            );
        }

        final List<Notam> notams = new ArrayList<>();
        RateLimitException rateLimitException = null;

        for (CompletableFuture<List<Notam>> f : futures) {
            try {
                notams.addAll(f.join());
            } catch (CompletionException ce) {
                final Throwable cause = ce.getCause();
                if (cause instanceof RateLimitException re) {
                    // remember the first RateLimitException to throw again later
                    if (rateLimitException == null) {
                        rateLimitException = re;
                    }
                } else {
                    logger.warn("Waypoint fetch failed with unexpected error: {}", cause.toString());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Stack trace for unexpected error:", cause);
                    }
                }
            }
        }

        // If we hit the FAA rate limit in any parallel task, throw it
        if (rateLimitException != null) {
            throw rateLimitException;
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

        return notams;
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

        final StringBuilder mapsUrl = new StringBuilder("https://www.google.com/maps/dir/");
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
     * Fetches NOTAMs for a specified geographic location and radius using provided credentials.
     *
     * @param latitude   the latitude of the location to fetch NOTAMs for
     * @param longitude  the longitude of the location to fetch NOTAMs for
     * @param radiusNm   the radius (in nautical miles) around the location to search for NOTAMs
     * @param credentials the API credentials to use for this request
     * @return           list of NOTAMs
     * @throws Exception if an error occurs during the API request or response handling
     */
    public List<Notam> fetchForLocation(double latitude, double longitude, int radiusNm, ApiCredentials credentials)
            throws Exception {

        final long t0 = System.currentTimeMillis();

        final FaaNotamApiWrapper.QueryParamsBuilder queryParams =
                new FaaNotamApiWrapper.QueryParamsBuilder(latitude, longitude, radiusNm)
                        .pageSize(200)
                        .credentials(credentials);

        final List<String> response = FaaNotamApiWrapper.fetchAllPages(queryParams, HTTP_TIMEOUT_SECONDS);
        List<Notam> waypointNotams = response.stream()
                .map( parser::parseGeoJson ).flatMap( List::stream )
                .toList();

        final long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Single HTTP fetch took {} ms ({} NOTAMs)",
                    (t1 - t0), waypointNotams.size());
        }

        return waypointNotams;
    }

    /**
     * Fetches NOTAMs for a location using the first credential pair (for backward compatibility).
     */
    public List<Notam> fetchForLocation(double latitude, double longitude, int radiusNm)
            throws Exception {
        return fetchForLocation(latitude, longitude, radiusNm, apiCredentials.get(0));
    }

    /**
     * Fetches NOTAMs for an airport using provided credentials.
     */
    public List<Notam> fetchForAirport(final String airportCode, ApiCredentials credentials)
            throws Exception {
        final long t0 = System.currentTimeMillis();

        final FaaNotamApiWrapper.QueryParamsBuilder queryParams =
                new FaaNotamApiWrapper.QueryParamsBuilder(airportCode)
                        .credentials(credentials);

        final List<String> response = FaaNotamApiWrapper.fetchAllPages(queryParams);

        List<Notam> notams = new ArrayList<>();
        response.forEach(p -> notams.addAll(parser.parseGeoJson(p)));

        final long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Single HTTP fetch took {} ms ({} NOTAMs)", (t1 - t0), notams.size());
        }
        return notams;
    }

    /**
     * Fetches NOTAMs for an airport using the first credential pair (for backward compatibility).
     */
    public List<Notam> fetchForAirport(final String airportCode)
            throws Exception {
        return fetchForAirport(airportCode, apiCredentials.get(0));
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
        final Optional<Coordinate> coords = airportDirectory.getCoordinates(airportCode);
        if (coords.isEmpty()) {
            throw new IllegalArgumentException("No coordinates found for airport: " + airportCode);
        }
        return coords.get();
    }
    /**
     * Shuts down the executor service to stop background threads
     * and allow the application to terminate cleanly.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}