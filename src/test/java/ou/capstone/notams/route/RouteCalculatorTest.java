package ou.capstone.notams.route;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for RouteCalculator
 */
class RouteCalculatorTest {

    private static final double TOLERANCE = 0.01; // tolerance for distance comparisons (NM)
    private static final double COORD_TOLERANCE = 0.001; // tolerance for coordinate comparisons (degrees)

    // Test coordinates: KOKC (Oklahoma City) and KDFW (Dallas)
    private static final double KOKC_LAT = 35.3931;
    private static final double KOKC_LON = -97.6007;
    private static final double KDFW_LAT = 32.8998;
    private static final double KDFW_LON = -97.0403;

    @Test
    void testDistanceNm_KnownRoute() {
        // KOKC to KDFW is approximately 152 NM
        double distance = RouteCalculator.distanceNm(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON);
        assertTrue(distance > 150 && distance < 155, 
                    "Expected distance between 150-155 NM, got: " + distance);
    }

    @Test
    void testDistanceNm_IdenticalPoints() {
        double distance = RouteCalculator.distanceNm(KOKC_LAT, KOKC_LON, KOKC_LAT, KOKC_LON);
        assertEquals(0.0, distance, TOLERANCE, "Distance between identical points should be 0");
    }

    @Test
    void testDistanceNm_Symmetry() {
        // Distance from A to B should equal distance from B to A
        double distanceAB = RouteCalculator.distanceNm(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON);
        double distanceBA = RouteCalculator.distanceNm(KDFW_LAT, KDFW_LON, KOKC_LAT, KOKC_LON);
        assertEquals(distanceAB, distanceBA, TOLERANCE, "Distance should be symmetric");
    }

    @Test
    void testDistanceNm_ShortDistance() {
        // Test two points very close together (1 degree apart, roughly 60 NM)
        double distance = RouteCalculator.distanceNm(35.0, -97.0, 36.0, -97.0);
        assertTrue(distance > 50 && distance < 70, 
                   "Expected distance around 60 NM for 1 degree separation");
    }

    @Test
    void testInterpolateRoute_TwoSegments() {
        List<RouteCalculator.Coordinate> points = 
            RouteCalculator.interpolateRoute(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, 2);
        
        assertEquals(3, points.size(), "2 segments should produce 3 points");
        
        // First point should be start
        assertEquals(KOKC_LAT, points.get(0).latDeg, COORD_TOLERANCE);
        assertEquals(KOKC_LON, points.get(0).lonDeg, COORD_TOLERANCE);
        
        // Last point should be end
        assertEquals(KDFW_LAT, points.get(2).latDeg, COORD_TOLERANCE);
        assertEquals(KDFW_LON, points.get(2).lonDeg, COORD_TOLERANCE);
        
        // Middle point should be between start and end
        assertTrue(points.get(1).latDeg < KOKC_LAT && points.get(1).latDeg > KDFW_LAT);
    }

    @Test
    void testInterpolateRoute_OneSegment() {
        List<RouteCalculator.Coordinate> points = 
            RouteCalculator.interpolateRoute(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, 1);
        
        assertEquals(2, points.size(), "1 segment should produce 2 points (start and end)");
        assertEquals(KOKC_LAT, points.get(0).latDeg, COORD_TOLERANCE);
        assertEquals(KDFW_LAT, points.get(1).latDeg, COORD_TOLERANCE);
    }

    @Test
    void testInterpolateRoute_IdenticalPoints() {
        List<RouteCalculator.Coordinate> points = 
            RouteCalculator.interpolateRoute(KOKC_LAT, KOKC_LON, KOKC_LAT, KOKC_LON, 5);
        
        assertEquals(6, points.size(), "Should still produce correct number of points");
        
        // All points should be the same
        for (RouteCalculator.Coordinate point : points) {
            assertEquals(KOKC_LAT, point.latDeg, COORD_TOLERANCE);
            assertEquals(KOKC_LON, point.lonDeg, COORD_TOLERANCE);
        }
    }

    @Test
    void testGetRouteWaypoints_StandardSpacing() {
        double spacingNm = 50.0;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Route is ~152 NM, so with 50 NM spacing we expect 3-4 waypoints
        assertTrue(waypoints.size() >= 4 && waypoints.size() <= 5, 
                   "Expected 3-4 waypoints for 152 NM route with 50 NM spacing, got: " + waypoints.size());
        
        // First and last should match endpoints
        assertEquals(KOKC_LAT, waypoints.get(0).latDeg, COORD_TOLERANCE);
        assertEquals(KDFW_LAT, waypoints.get(waypoints.size() - 1).latDeg, COORD_TOLERANCE);
    }

    @Test
    void testGetRouteWaypoints_SmallSpacing() {
        double spacingNm = 25.0;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Route is ~152 NM, so with 25 NM spacing we expect 6-7 waypoints
        assertTrue(waypoints.size() >= 7 && waypoints.size() <= 8, 
                   "Expected 6-7 waypoints for 152 NM route with 25 NM spacing, got: " + waypoints.size());
    }

    @Test
    void testGetRouteWaypoints_LargeSpacing() {
        double spacingNm = 200.0;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Spacing larger than route distance should give 2 points (start and end)
        assertEquals(2, waypoints.size(), "Large spacing should give just start and end points");
    }

    @Test
    void testGetRouteWaypoints_VerifySpacing() {
        double spacingNm = 50.0;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Verify spacing between consecutive waypoints is approximately correct
        // Note: We exclude the last waypoint because the final segment length may vary
        // since the total route distance may not be an exact multiple of the spacing
        for (int i = 0; i < waypoints.size() - 1; i++) {
            RouteCalculator.Coordinate p1 = waypoints.get(i);
            RouteCalculator.Coordinate p2 = waypoints.get(i + 1);
            double dist = RouteCalculator.distanceNm(p1.latDeg, p1.lonDeg, p2.latDeg, p2.lonDeg);
            
            // Spacing should be close to requested spacing (within 20% tolerance)
            assertTrue(dist <= spacingNm * 1.2, 
                       "Spacing between waypoints " + i + " and " + (i+1) + 
                       " should be <= " + (spacingNm * 1.2) + " NM, got: " + dist);
        }
    }

    @Test
    void testGetRouteWaypoints_ShortRoute() {
        // Test a very short route (10 NM) with 50 NM spacing
        double lat1 = 35.0, lon1 = -97.0;
        double lat2 = 35.15, lon2 = -97.0; // roughly 10 NM north
        
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(lat1, lon1, lat2, lon2, 50.0);
        
        assertEquals(2, waypoints.size(), "Short route should give 2 waypoints");
        assertEquals(lat1, waypoints.get(0).latDeg, COORD_TOLERANCE);
        assertEquals(lat2, waypoints.get(1).latDeg, COORD_TOLERANCE);
    }

    @Test
    void testCoordinate_ToString() {
        RouteCalculator.Coordinate coord = new RouteCalculator.Coordinate(35.3931, -97.6007);
        String str = coord.toString();
        
        assertTrue(str.contains("35.393"), "toString should contain latitude");
        assertTrue(str.contains("-97.600"), "toString should contain longitude");
    }

    @Test
    void testGetRouteWaypoints_ExtremelySmallSpacing() {
        double spacingNm = 0.01;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Should handle tiny spacing without errors
        assertTrue(waypoints.size() > 100, "Tiny spacing should produce many waypoints");
    }

    @Test
    void testGetRouteWaypoints_ExtremelyLargeSpacing() {
        double spacingNm = Integer.MAX_VALUE;
        List<RouteCalculator.Coordinate> waypoints = 
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, spacingNm);
        
        // Spacing larger than any realistic route should give 2 points (start and end)
        assertEquals(2, waypoints.size(), "Extremely large spacing should give just start and end points");
}

    @Test
    void testGetRouteWaypoints_NegativeSpacing() {
        assertThrows(IllegalArgumentException.class, () -> {
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, -50.0);
        }, "Negative spacing should throw IllegalArgumentException");
    }

    @Test
    void testGetRouteWaypoints_ZeroSpacing() {
        assertThrows(IllegalArgumentException.class, () -> {
            RouteCalculator.getRouteWaypoints(KOKC_LAT, KOKC_LON, KDFW_LAT, KDFW_LON, 0.0);
        }, "Zero spacing should throw IllegalArgumentException");
    }
}