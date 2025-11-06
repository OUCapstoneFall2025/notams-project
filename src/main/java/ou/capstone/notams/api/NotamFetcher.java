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

    // HTTP per-request timeout (seconds), configurable for experimentation
    private static final int HTTP_TIMEOUT_SECONDS =
            Integer.parseInt(System.getenv().getOrDefault("NOTAM_HTTP_TIMEOUT_SECONDS", "30"));

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

        // Measure total fetch time
        final long fetchStart = System.currentTimeMillis();

        int index = 1;
        for (Coordinate waypoint : waypoints) {
            final long singleFetchStart = System.currentTimeMillis();
            String response = fetchForLocation(waypoint.getLatitude(), waypoint.getLongitude(), QUERY_RADIUS_NM);
            final long singleFetchEnd = System.currentTimeMillis();
            if (logger.isDebugEnabled()) {
                logger.debug("Fetch {}/{} at ({}, {}) took {} ms",
                        index++, waypoints.size(),
                        waypoint.getLatitude(), waypoint.getLongitude(),
                        (singleFetchEnd - singleFetchStart));
            }
            responses.add(response);
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
        final ConnectToAPI.QueryParamsBuilder queryParams = new ConnectToAPI.QueryParamsBuilder(latitude, longitude, radiusNm)
                .pageSize("100"); // NotamFetcher uses pageSize 100

        final String response = ConnectToAPI.fetchRawJson(queryParams, HTTP_TIMEOUT_SECONDS);

        final long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("Single HTTP fetch took {} ms",
                    (t1 - t0));
        }

        return response;
    }

    /**
     * Retrieves the latitude and longitude coordinates for a given airport code.
     * Accepts both IATA (3-letter) and ICAO (4-letter) codes.
     * Uses the AirportDirectory to look up coordinates from the airport CSV database.
     *
     * @param airportCode the IATA or ICAO code of the airport
     * @return an array containing latitude and longitude as [lat, lon]
     * @throws IllegalArgumentException if the airport code is not found
     */
    private Coordinate getAirportCoordinates(String airportCode) {
        // Convert to ICAO code for FAA API compatibility
        String icaoCode = airportDirectory.getIcaoCode(airportCode);
        if (icaoCode == null) {
            throw new IllegalArgumentException("Unknown airport: " + airportCode);
        }
        Optional<Coordinate> coords = airportDirectory.getCoordinates(icaoCode);
        if (coords.isEmpty()) {
            throw new IllegalArgumentException("No coordinates found for airport: " + icaoCode);
        }
        return coords.get();
    }

}