package ou.capstone.notams.api;

import ou.capstone.notams.route.RouteCalculator;
import ou.capstone.notams.route.Coordinate;
import ou.capstone.notams.validation.AirportDirectory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final AirportDirectory airportDirectory;
    
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
        
        // Get coordinates for departure and destination
         Coordinate depCoords = getAirportCoordinates(departureCode);
         Coordinate destCoords = getAirportCoordinates(destinationCode);

         // Calculate waypoints along the route
         List<Coordinate> waypoints = RouteCalculator.getRouteWaypoints(
                 depCoords.getLatitude(), depCoords.getLongitude(),
                 destCoords.getLatitude(), destCoords.getLongitude(),
                 WAYPOINT_SPACING_NM
         );
        
        List<String> responses = new ArrayList<>();
        
        // Fetch NOTAMs at each waypoint
        for (Coordinate waypoint : waypoints) {
            String response = fetchForLocation(waypoint.getLatitude(), waypoint.getLongitude(), QUERY_RADIUS_NM);
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