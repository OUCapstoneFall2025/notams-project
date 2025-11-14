package ou.capstone.notams.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.naming.RefAddr;

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
			// TODO need to fix this
//            List<String> pages = fetchAllForLocation(waypoint.getLatitude(), waypoint.getLongitude(), QUERY_RADIUS_NM);

			List<String> pages = fetchAll(waypoint.getLatitude(), waypoint.getLongitude(), QUERY_RADIUS_NM );
			//List<String> pages = ConnectToAPI.fetchAll();
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

	private List<String> fetchAll( final String airportCode ) {
		ConnectToAPI.QueryParamsBuilder queryBuilder = new ConnectToAPI.QueryParamsBuilder( airportCode );
		queryBuilder.pageNum( "1" );
		return fetchAll(queryBuilder);
	}

	private List<String> fetchAll( double latitude, double longitude, int queryRadiusNm )
	{
		ConnectToAPI.QueryParamsBuilder queryBuilder = new ConnectToAPI.QueryParamsBuilder( latitude, longitude,queryRadiusNm );
		queryBuilder.pageNum( "1" );
		return fetchAll(queryBuilder);
	}

	private List<String> fetchAll( final ConnectToAPI.QueryParamsBuilder queryBuilder )
	{
		List<String> pages = new ArrayList<>();

		try {
			String result = ConnectToAPI.fetchRawJson( queryBuilder );
		}
		catch( Exception e ) {
			throw new RuntimeException( e );
		}

		pages.add( result );

		// do I have more pages to fetch?
		// inspect result
		// find the total number of pages

		while (  currentPage <= maxPagesInResult ) {
			queryBuilder.pageNum( currentPage );
			String nextPage = ConnectToAPI.fetchRawJson( queryBuilder );
			pages.add(nextPage);
		}
		return pages;
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
		final List<String> pages = fetchAll( latitude,longitude, radiusNm);
		final String


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

    // ------------------------------------------------------------------
    // Added (non-breaking) pagination helpers to handle >1000 NOTAMs.
    // These do not remove or change any existing comments or behavior.
    // ------------------------------------------------------------------

//    // Configurable page size and hard cap on total pages (safety guard).
//    private static final int PAGE_SIZE = Integer.parseInt(System.getenv().getOrDefault("NOTAM_PAGE_SIZE", "500"));
//    private static final int MAX_PAGES = Integer.parseInt(System.getenv().getOrDefault("NOTAM_MAX_PAGES", "200"));
//
//    /**
//     * Fetch ALL pages of NOTAMs for a given location (radiusNm) by iterating page=1..MAX_PAGES.
//     * Returns a list of raw response bodies (GeoJSON strings), one per page.
//     * Keeps the original single-page method available and unchanged.
//     */
//    public List<String> fetchAllForLocation(double latitude, double longitude, int radiusNm) throws Exception {
//        List<String> pages = new ArrayList<>();
//        for (int page = 1; page <= MAX_PAGES; page++) {
//            String body = fetchPageForLocation(latitude, longitude, radiusNm, page, PAGE_SIZE);
//            pages.add(body);
//
//            // If consumers later parse JSON, a robust break is: if (features.size() < PAGE_SIZE) break;
//            // As a lightweight hint without parsing, stop early on small bodies.
//            if (body.length() < 10_000) {
//                break;
//            }
//        }
//        return pages;
//    }

//    /**
//     * Fetch a single page with explicit page & pageSize.
//     * Leaves the original fetchForLocation(...) intact.
//     */
//    private String fetchPageForLocation(double latitude, double longitude, int radiusNm, int page, int pageSize) throws Exception {
//        final long t0 = System.currentTimeMillis();
//
//        String queryString = String.format(
//            "responseFormat=%s&latitude=%s&longitude=%s&radius=%s&page=%s&pageSize=%s",
//            enc("geoJson"),
//            enc(String.valueOf(latitude)),
//            enc(String.valueOf(longitude)),
//            enc(String.valueOf(radiusNm)),
//            enc(String.valueOf(page)),
//            enc(String.valueOf(pageSize))
//        );
//
//        URI uri = new URI("https", "external-api.faa.gov",
//            "/notamapi/v1/notams", queryString, null);
//
//        HttpRequest request = HttpRequest.newBuilder(uri)
//            .GET()
//            .header("client_id", clientId)
//            .header("client_secret", clientSecret)
//            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
//            .build();
//
//        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//        final long t1 = System.currentTimeMillis();
//        if (logger.isDebugEnabled()) {
//            logger.debug("Paged HTTP fetch took {} ms (status {}, page {}, size {})",
//                    (t1 - t0), response.statusCode(), page, pageSize);
//        }
//
//        if (response.statusCode() != 200) {
//            throw new RuntimeException("API error for location (" + latitude + ", " + longitude + "): "
//                + response.statusCode() + " - " + response.body());
//        }
//
//        return response.body();
//    }
}
