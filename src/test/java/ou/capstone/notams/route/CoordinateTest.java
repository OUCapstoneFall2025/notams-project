package ou.capstone.notams.route;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for Coordinate class
 */
class CoordinateTest {

    private static final double TOLERANCE = 0.000001; // Tolerance for double comparisons

    @Test
    void testConstructor_BasicCoordinates() {
        Coordinate coord = new Coordinate(35.3931, -97.6007);
        
        assertEquals(35.3931, coord.getLatitude(), TOLERANCE, "Latitude should match constructor value");
        assertEquals(-97.6007, coord.getLongitude(), TOLERANCE, "Longitude should match constructor value");
    }

    @Test
    void testConstructor_ZeroCoordinates() {
        Coordinate coord = new Coordinate(0.0, 0.0);
        
        assertEquals(0.0, coord.getLatitude(), TOLERANCE, "Zero latitude should be valid");
        assertEquals(0.0, coord.getLongitude(), TOLERANCE, "Zero longitude should be valid");
    }

    @Test
    void testConstructor_PoleCoordinates() {
        // North Pole
        Coordinate northPole = new Coordinate(90.0, 0.0);
        assertEquals(90.0, northPole.getLatitude(), TOLERANCE, "North pole latitude should be 90");
        
        // South Pole
        Coordinate southPole = new Coordinate(-90.0, 0.0);
        assertEquals(-90.0, southPole.getLatitude(), TOLERANCE, "South pole latitude should be -90");
    }

    @Test
    void testConstructor_DateLineCoordinates() {
        // International Date Line (180 degrees)
        Coordinate eastDateLine = new Coordinate(0.0, 180.0);
        assertEquals(180.0, eastDateLine.getLongitude(), TOLERANCE);
        
        Coordinate westDateLine = new Coordinate(0.0, -180.0);
        assertEquals(-180.0, westDateLine.getLongitude(), TOLERANCE);
    }

    @Test
    void testConstructor_NegativeCoordinates() {
        // Southern and Western hemispheres
        Coordinate coord = new Coordinate(-33.8688, -151.2093);
        
        assertEquals(-33.8688, coord.getLatitude(), TOLERANCE);
        assertEquals(-151.2093, coord.getLongitude(), TOLERANCE);
    }

    @Test
    void testToString_Format() {
        Coordinate coord = new Coordinate(35.393100, -97.600700);
        String result = coord.toString();
        
        // Should format with 6 decimal places in parentheses
        assertTrue(result.contains("35.393100"), "toString should contain latitude with 6 decimals");
        assertTrue(result.contains("-97.600700"), "toString should contain longitude with 6 decimals");
        assertTrue(result.startsWith("("), "toString should start with (");
        assertTrue(result.endsWith(")"), "toString should end with )");
    }

    @Test
    void testToString_RoundingBehavior() {
        // Test that values are properly formatted with 6 decimals
        Coordinate coord = new Coordinate(12.3456789, -98.7654321);
        String result = coord.toString();
        
        assertTrue(result.contains("12.345679"), "Should round to 6 decimal places");
        assertTrue(result.contains("-98.765432"), "Should round to 6 decimal places");
    }

    @Test
    void testToString_ZeroCoordinates() {
        Coordinate coord = new Coordinate(0.0, 0.0);
        String result = coord.toString();
        
        assertTrue(result.contains("0.000000"), "Zero should be formatted with 6 decimals");
    }

    @Test
    void testToString_NegativeZero() {
        // Test edge case of -0.0
        Coordinate coord = new Coordinate(-0.0, -0.0);
        String result = coord.toString();
        
        // Should handle negative zero gracefully
        assertTrue(result.contains("0.000000") || result.contains("-0.000000"));
    }

    @Test
    void testFieldsAreFinal() {
        // Verify immutability by creating coordinate and ensuring fields don't change
        Coordinate coord = new Coordinate(40.0, -75.0);
        double originalLat = coord.getLatitude();
        double originalLon = coord.getLongitude();
        
        // Create another coordinate (fields are final, so we can't reassign)
        Coordinate coord2 = new Coordinate(50.0, -80.0);
        
        // Original should be unchanged
        assertEquals(originalLat, coord.getLatitude(), TOLERANCE, "Coordinate should be immutable");
        assertEquals(originalLon, coord.getLongitude(), TOLERANCE, "Coordinate should be immutable");
    }

    @Test
    void testMultipleInstances() {
        // Verify multiple instances are independent
        Coordinate coord1 = new Coordinate(10.0, 20.0);
        Coordinate coord2 = new Coordinate(30.0, 40.0);
        
        assertEquals(10.0, coord1.getLatitude(), TOLERANCE);
        assertEquals(30.0, coord2.getLatitude(), TOLERANCE);
        
        // Instances should be independent
        assertTrue(coord1.getLatitude() != coord2.getLatitude());
        assertTrue(coord1.getLongitude() != coord2.getLongitude());
    }

    @Test
    void testConstructor_ExtremePrecision() {
        Coordinate coord = new Coordinate(12.123456789012345, -98.987654321098765);
        // Verify it handles many decimal places without errors
        assertEquals(12.123456789012345, coord.getLatitude(), TOLERANCE);
    }

    @Test
    void testLatitudeAboveRange_throwsException() {
        Exception e = assertThrows(IllegalArgumentException.class, 
            () -> new Coordinate(91.0, 0.0));
        assertTrue(e.getMessage().contains("Latitude"), "Expected Latitude range exception");
    }

    @Test
    void testLatitudeBelowRange_throwsException() {
        Exception e = assertThrows(IllegalArgumentException.class, 
            () -> new Coordinate(-91.0, 0.0));
        assertTrue(e.getMessage().contains("Latitude"), "Expected Latitude range exception");
    }

    @Test
    void testLongitudeAboveRange_throwsException() {
        Exception e = assertThrows(IllegalArgumentException.class, 
            () -> new Coordinate(0.0, 181.0));
        assertTrue(e.getMessage().contains("Longitude"), "Expected Longitude range exception");
    }

    @Test
    void testLongitudeBelowRange_throwsException() {
        Exception e = assertThrows(IllegalArgumentException.class, 
            () -> new Coordinate(0.0, -181.0));
        assertTrue(e.getMessage().contains("Longitude"), "Expected Longitude range exception");
    }
}