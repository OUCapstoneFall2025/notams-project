package ou.capstone.notams.api;

import ou.capstone.notams.route.RouteCalculator;
import ou.capstone.notams.route.RouteCalculator.Coordinate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches raw NOTAM data from the FAA API for given routes and airports.
 * CCS-16: Responsible for API connection and data retrieval along flight routes.
 * Requires FAA_CLIENT_ID and FAA_CLIENT_SECRET environment variables to be set.
 * If either is missing, the constructor will throw IllegalStateException.
 */
public class NotamFetcher {
    
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    
    // Radius in nautical miles for location queries
    private static final int QUERY_RADIUS_NM = 50;
    
    // Spacing between waypoints along route
    private static final double WAYPOINT_SPACING_NM = 50.0;
    
    /**
     * Constructs a NotamFetcher.
     * Requires FAA_CLIENT_ID and FAA_CLIENT_SECRET environment variables to be set.
     * @throws IllegalStateException if required environment variables are not set
     */
    public NotamFetcher() {
        this.clientId = System.getenv("FAA_CLIENT_ID");
        this.clientSecret = System.getenv("FAA_CLIENT_SECRET");
        
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException(
                "FAA_CLIENT_ID or FAA_CLIENT_SECRET not set in environment!");
        }
        
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Fetch raw NOTAM JSON data for a flight route between two airports.
     * Queries along the great-circle route using waypoints.
     *
     * @param departureIcao ICAO code of the departure airport
     * @param destinationIcao ICAO code of the destination airport
     * @return List of raw NOTAM API response strings for each waypoint
     * @throws Exception if API call fails or airport code is unknown
     * @see #fetchForLocation(double, double, int)
     */
    public List<String> fetchForRoute(String departureIcao, String destinationIcao) 
            throws Exception {
        
        // Get coordinates for departure and destination
        double[] depCoords = getAirportCoordinates(departureIcao);
        double[] destCoords = getAirportCoordinates(destinationIcao);
        
        // Calculate waypoints along the route
        List<Coordinate> waypoints = RouteCalculator.getRouteWaypoints(
            depCoords[0], depCoords[1],
            destCoords[0], destCoords[1],
            WAYPOINT_SPACING_NM
        );
        
        List<String> responses = new ArrayList<>();
        
        // Fetch NOTAMs at each waypoint
        for (Coordinate waypoint : waypoints) {
            String response = fetchForLocation(waypoint.latDeg, waypoint.lonDeg, QUERY_RADIUS_NM);
            responses.add(response);
        }
        
        return responses;
    }
    
    /**
     * Fetches NOTAMs (Notice to Airmen) for a specified geographic location and radius.
     * Sends a GET request to the FAA NOTAM API using the provided latitude, longitude, and radius in nautical miles.
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
        
        String queryString = String.format(
            "responseFormat=%s&latitude=%s&longitude=%s&radius=%s&pageSize=%s&sortBy=%s&sortOrder=%s",
            enc("geoJson"),
            enc(String.valueOf(latitude)),
            enc(String.valueOf(longitude)),
            enc(String.valueOf(radiusNm)),
            enc("100"),
            enc("effectiveStartDate"),
            enc("Desc")
        );
        
        URI uri = new URI("https", "external-api.faa.gov", 
            "/notamapi/v1/notams", queryString, null);
        
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .header("client_id", clientId)
            .header("client_secret", clientSecret)
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API error for location (" + latitude + ", " + longitude + "): " 
                + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }
    
    /**
     * Get airport coordinates.
     * Uses a static map for lookup; extend as needed.
     * TODO: Replace with airport database lookup (future ticket)
     */
    private static final Map<String, double[]> AIRPORT_COORDS = new java.util.HashMap<>();
    static {
        AIRPORT_COORDS.put("KOKC", new double[]{35.3931, -97.6007});
        AIRPORT_COORDS.put("KDFW", new double[]{32.8998, -97.0403});
        AIRPORT_COORDS.put("KJFK", new double[]{40.6413, -73.7781});
        AIRPORT_COORDS.put("KLAX", new double[]{33.9416, -118.4085});
        AIRPORT_COORDS.put("KORD", new double[]{41.9742, -87.9073});
        AIRPORT_COORDS.put("KATL", new double[]{33.6407, -84.4277});
    }
    /**
     * Retrieves the latitude and longitude coordinates for a given airport ICAO code.
     * Throws IllegalArgumentException if the ICAO code is not found in the static map.
     *
     * @param icaoCode the ICAO code of the airport
     * @return an array containing latitude and longitude as [lat, lon]
     * @throws IllegalArgumentException if the ICAO code is unknown
     * @see AIRPORT_COORDS
     * TODO: Replace with airport database lookup (future ticket)
     */
    private static double[] getAirportCoordinates(String icaoCode) {
        double[] coords = AIRPORT_COORDS.get(icaoCode);
        if (coords == null) {
            throw new IllegalArgumentException("Unknown airport: " + icaoCode);
        }
        return coords;
    }
    
    /**
     * URL-encodes a string for safe use as an HTTP query parameter.
     * 
     * @param s the string to encode
     * @return the URL-encoded string
     * @throws RuntimeException if UTF-8 encoding is not supported (should never happen)
     */
    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported, so this should never happen
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }
}