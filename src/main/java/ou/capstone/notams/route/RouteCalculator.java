package ou.capstone.notams.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RouteCalculator
 *
 * - Computes great-circle route points between two coordinates
 * - Samples points along the route by spacing in nautical miles
 *
 */
public class RouteCalculator {
    private static final Logger logger = LoggerFactory.getLogger(RouteCalculator.class);

    // ---- Small helper record for lat/lon ----
    public static final class Coordinate {
        public final double latDeg;
        public final double lonDeg;
        public Coordinate(double latDeg, double lonDeg) {
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
        }
        @Override public String toString() { return String.format(Locale.US, "(%.6f, %.6f)", latDeg, lonDeg); }
    }

    // Earth radius (nautical miles)
    private static final double R_NM = 3440.065;

    // ---------- Great-circle math ----------

    /** Central angle (radians) between two points via Haversine formula. */
    private static double centralAngleRad(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lon1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lon2Deg);

        final double dLat = lat2 - lat1;
        final double dLon = lon2 - lon1;

        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * Math.asin(Math.sqrt(a));  // radians
    }

    /** Great-circle distance in nautical miles. */
    public static double distanceNm(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        logger.debug("Calculating great-circle distance between ({}, {}) and ({}, {})", 
                     lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        double distance = R_NM * centralAngleRad(lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        logger.debug("Calculated distance: {:.2f} nautical miles", distance);
        return distance;
    }

    /**
     * Spherical linear interpolation (slerp) points (inclusive) along the great circle.
     * Returns (segments + 1) points including start and end.
     */
    public static List<Coordinate> interpolateRoute(double lat1Deg, double lon1Deg,
                                                double lat2Deg, double lon2Deg,
                                                int segments) {
        logger.debug("Starting route interpolation with {} segments between ({}, {}) and ({}, {})", 
                     segments, lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        
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

        List<Coordinate> coords = new ArrayList<>(segments + 1);

        // If points are identical or nearly so, just return the start
        if (theta < 1e-12) {
            logger.debug("Points are identical or nearly identical (theta={}), returning {} identical coordinates", 
                         theta, segments + 1);
            for (int i = 0; i <= segments; i++) coords.add(new Coordinate(lat1Deg, lon1Deg));
            return coords;
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

            coords.add(new Coordinate(Math.toDegrees(phi), Math.toDegrees(lambda)));
        }
        logger.debug("Route interpolation completed. Generated {} coordinates", coords.size());
        return coords;
    }

    /**
     * Produce waypoints separated by ~spacingNm along the route.
     * Ensures overlap when used with a similar query radius.
     */
    public static List<Coordinate> getRouteWaypoints(double lat1Deg, double lon1Deg,
                                                 double lat2Deg, double lon2Deg,
                                                 double spacingNm) {
        if (spacingNm <= 0) {
            logger.error("Invalid spacing value: {} NM. Spacing must be positive.", spacingNm);
            throw new IllegalArgumentException("Spacing must be positive, got: " + spacingNm);
        }
        
        logger.info("Generating route waypoints with {} NM spacing between ({}, {}) and ({}, {})", 
                    spacingNm, lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        
        double totalNm = distanceNm(lat1Deg, lon1Deg, lat2Deg, lon2Deg);
        int segments = Math.max(1, (int) Math.ceil(totalNm / spacingNm));
        
        logger.info("Total route distance: {:.2f} NM, calculated {} segments for {} NM spacing", 
                    totalNm, segments, spacingNm);
        
        List<Coordinate> waypoints = interpolateRoute(lat1Deg, lon1Deg, lat2Deg, lon2Deg, segments);
        logger.info("Generated {} waypoints for route", waypoints.size());
        
        return waypoints;
    }

    // ---------- Demo main ----------
    public static void main(String[] args) throws Exception {
        logger.info("Starting RouteCalculator demo");
        
        // Example: KOKC -> KDFW (approx coords)
        final double kokcLat = 35.3931, kokcLon = -97.6007;
        final double kdfwLat = 32.8998, kdfwLon = -97.0403;

        // Choose sample spacing
        final double spacingNm = 25.0; // distance between sample points
        
        logger.info("Demo route: KOKC ({}, {}) -> KDFW ({}, {}) with {} NM spacing", 
                    kokcLat, kokcLon, kdfwLat, kdfwLon, spacingNm);

        System.out.printf("Route distance: ~%.1f NM%n",
                distanceNm(kokcLat, kokcLon, kdfwLat, kdfwLon));

        var wpts = getRouteWaypoints(kokcLat, kokcLon, kdfwLat, kdfwLon, spacingNm);
        System.out.println("Waypoints: " + wpts.size());
        for (int i = 0; i < wpts.size(); i++){
            System.out.println("Waypoint " + (i+1) + ": " + wpts.get(i));
        }
        
        logger.info("RouteCalculator demo completed successfully");
        System.out.println("Finished running.");
    }
}
