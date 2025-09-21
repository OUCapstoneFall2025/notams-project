package org.example.notams;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * RouteNotams
 *
 * - Computes great-circle route points between two coordinates
 * - Samples points along the route by spacing in nautical miles
 * - Queries FAA NOTAM API around each point with a radius (NM)
 *
 * Requirements: Java 11+ (uses java.net.http.HttpClient)
 */
public class RouteNotams {

    // ---- Small helper record for lat/lon ----
    public static final class LatLon {
        public final double latDeg;
        public final double lonDeg;
        public LatLon(double latDeg, double lonDeg) {
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
        }
        @Override public String toString() { return String.format(Locale.US, "(%.6f, %.6f)", latDeg, lonDeg); }
    }

    // Earth radius (nautical miles)
    private static final double R_NM = 3440.065;

    // ---------- Great-circle math ----------

    /** Central angle (radians) between two points via Haversine formula. */
    public static double centralAngleRad(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        double φ1 = Math.toRadians(lat1Deg);
        double λ1 = Math.toRadians(lon1Deg);
        double φ2 = Math.toRadians(lat2Deg);
        double λ2 = Math.toRadians(lon2Deg);

        double dφ = φ2 - φ1;
        double dλ = λ2 - λ1;

        double a = Math.sin(dφ / 2) * Math.sin(dφ / 2) +
                   Math.cos(φ1) * Math.cos(φ2) * Math.sin(dλ / 2) * Math.sin(dλ / 2);
        return 2 * Math.asin(Math.sqrt(a));  // radians
    }

    /** Great-circle distance in nautical miles. */
    public static double distanceNm(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        return R_NM * centralAngleRad(lat1Deg, lon1Deg, lat2Deg, lon2Deg);
    }

    /**
     * Spherical linear interpolation (slerp) points (inclusive) along the great circle.
     * Returns (segments + 1) points including start and end.
     */
    public static List<LatLon> interpolateRoute(double lat1Deg, double lon1Deg,
                                                double lat2Deg, double lon2Deg,
                                                int segments) {
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lon1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lon2Deg);

        // Convert endpoints to 3D unit vectors
        double x1 = Math.cos(lat1) * Math.cos(lon1);
        double y1 = Math.cos(lat1) * Math.sin(lon1);
        double z1 = Math.sin(lat1);

        double x2 = Math.cos(lat2) * Math.cos(lon2);
        double y2 = Math.cos(lat2) * Math.sin(lon2);
        double z2 = Math.sin(lat2);

        // Angle between vectors
        double dot = (x1 * x2) + (y1 * y2) + (z1 * z2);
        // Clamp to avoid NaN from rounding
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double theta = Math.acos(dot);

        List<LatLon> out = new ArrayList<>(segments + 1);

        // If points are identical or nearly so, just return the start
        if (theta < 1e-12) {
            for (int i = 0; i <= segments; i++) out.add(new LatLon(lat1Deg, lon1Deg));
            return out;
        }

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double A = Math.sin((1 - t) * theta) / Math.sin(theta);
            double B = Math.sin(t * theta) / Math.sin(theta);

            double x = (A * x1) + (B * x2);
            double y = (A * y1) + (B * y2);
            double z = (A * z1) + (B * z2);

            double phi = Math.atan2(z, Math.sqrt(x * x + y * y));
            double lambda = Math.atan2(y, x);

            out.add(new LatLon(Math.toDegrees(phi), Math.toDegrees(lambda)));
        }
        return out;
    }

    /**
     * Produce waypoints separated by ~spacingNm along the route.
     * Ensures overlap when used with a similar query radius.
     */
    public static List<LatLon> sampleBySpacingNm(double lat1Deg, double lon1Deg,
                                                 double lat2Deg, double lon2Deg,
                                                 double spacingNm) {
        double totalNm = distanceNm(lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        int segments = Math.max(1, (int) Math.ceil(totalNm / spacingNm));
        return interpolateRoute(lat1Deg, lon1Deg, lat2Deg, lon2Deg, segments);
    }

    // ---------- FAA NOTAM API calls ----------

    private static final String FAA_BASE = "https://external-api.faa.gov/notamapi/v1/notams";

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * Calls the FAA NOTAM API (GeoJSON) using locationLatitude/Longitude/Radius.
     * Returns the raw JSON string.
     */
    public static String fetchNotamsGeoJson(double lat, double lon, int radiusNm,
                                            String clientId, String clientSecret,
                                            int pageSize, int pageNum) throws IOException, InterruptedException {
        // Build query parameters according to the RAML trait you were given
        String uri = FAA_BASE
                + "?responseFormat=" + encode("geoJson")
                + "&locationLatitude=" + encode(String.format(Locale.US, "%.6f", lat))
                + "&locationLongitude=" + encode(String.format(Locale.US, "%.6f", lon))
                + "&locationRadius=" + radiusNm
                + "&pageSize=" + pageSize
                + "&pageNum=" + pageNum;

        HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30))
                .header("client_id", clientId)
                .header("client_secret", clientSecret)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    /**
     * Sweep NOTAMs along a route: sample points, query at each point, collect raw JSON responses.
     * (You can later parse & de-duplicate by NOTAM id/number.)
     */
    public static List<String> fetchRouteNotams(double lat1Deg, double lon1Deg,
                                                double lat2Deg, double lon2Deg,
                                                double spacingNm, int radiusNm,
                                                String clientId, String clientSecret)
            throws IOException, InterruptedException {

        List<LatLon> waypoints = sampleBySpacingNm(lat1Deg, lon1Deg, lat2Deg, lon2Deg, spacingNm);
        List<String> jsonResponses = new ArrayList<>();

        for (LatLon p : waypoints) {
            System.out.printf(Locale.US, "Querying waypoint (%.5f, %.5f)%n", p.latDeg, p.lonDeg);
            String json = fetchNotamsGeoJson(p.latDeg, p.lonDeg, radiusNm, clientId, clientSecret, 100, 1);
            jsonResponses.add(json);
        }
        return jsonResponses;
    }

    private static int approxNotamCount(String geojson) {
    int count = 0, idx = 0;
    String needle = "\"type\":\"Feature\"";
    while ((idx = geojson.indexOf(needle, idx)) >= 0) { count++; idx += needle.length(); }
    return count;
    }


    // ---------- Demo main ----------
    public static void main(String[] args) throws Exception {
        // Example: KOKC -> KDFW (approx coords)
        double kokcLat = 35.3931, kokcLon = -97.6007;
        double kdfwLat = 32.8998, kdfwLon = -97.0403;

        // Environment (or replace with literals for a quick test)
        String clientId = System.getenv("FAA_CLIENT_ID");
        String clientSecret = System.getenv("FAA_CLIENT_SECRET");
        if (clientId == null || clientSecret == null) {
            System.err.println("Set FAA_CLIENT_ID and FAA_CLIENT_SECRET env vars first.");
            return;
        }

        // Choose corridor spacing and radius (NM)
        double spacingNm = 25.0; // distance between sample points
        int radiusNm = 25;       // circle radius at each sample point

        System.out.printf("Route distance: ~%.1f NM%n",
                distanceNm(kokcLat, kokcLon, kdfwLat, kdfwLon));

        List<String> results = fetchRouteNotams(
                kokcLat, kokcLon, kdfwLat, kdfwLon,
                spacingNm, radiusNm, clientId, clientSecret
        );

        // For now, just print sizes and first 200 chars of each result
        for (int i = 0; i < results.size(); i++) {
            String s = results.get(i);
            System.out.println("---- Waypoint " + i + " response ----");
            System.out.println(s.substring(0, Math.min(200, s.length())) + (s.length() > 200 ? "..." : ""));
        }

        // TODO:
        // - Parse GeoJSON (e.g., with Jackson or org.json),
        // - Collect each NOTAM "id" or "number",
        // - De-duplicate across responses,
        // - Persist or display as needed.
    }
}
