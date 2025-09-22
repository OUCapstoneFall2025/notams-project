package org.example.notams;

import java.util.*;

/**
 * RouteNotams
 *
 * - Computes great-circle route points between two coordinates
 * - Samples points along the route by spacing in nautical miles
 *
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
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lon1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lon2Deg);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
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

    // ---------- Demo main ----------
    public static void main(String[] args) throws Exception {
        // Example: KOKC -> KDFW (approx coords)
        double kokcLat = 35.3931, kokcLon = -97.6007;
        double kdfwLat = 32.8998, kdfwLon = -97.0403;

        // Choose sample spacing
        double spacingNm = 25.0; // distance between sample points

        System.out.printf("Route distance: ~%.1f NM%n",
                distanceNm(kokcLat, kokcLon, kdfwLat, kdfwLon));

        var wpts = sampleBySpacingNm(kokcLat, kokcLon, kdfwLat, kdfwLon, spacingNm);
        System.out.println("Waypoints: " + wpts.size());
        for (int i = 0; i < wpts.size(); i++){
            System.out.println("Waypoint " + (i+1) + ": " + wpts.get(i));
        }
    }
}
