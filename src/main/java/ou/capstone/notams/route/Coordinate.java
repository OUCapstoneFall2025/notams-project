package ou.capstone.notams.route;

import java.util.Locale;

/**
 * Immutable geographic coordinate (latitude/longitude in degrees).
 * Provides basic validation and formatted output.
 */
public final class Coordinate {

    public final double latDeg;
    public final double lonDeg;

    /**
     * Constructs a Coordinate object with validation.
     * 
     * @param latDeg latitude in degrees (-90 to +90)
     * @param lonDeg longitude in degrees (-180 to +180)
     * @throws IllegalArgumentException if latitude or longitude are out of range
     */
    public Coordinate(final double latDeg, final double lonDeg) {
        if (latDeg < -90.0 || latDeg > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and +90 degrees");
        }
        if (lonDeg < -180.0 || lonDeg > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and +180 degrees");
        }
        this.latDeg = latDeg;
        this.lonDeg = lonDeg;
    }

    /** @return latitude in degrees */
    public double getLatitude() {
        return latDeg;
    }

    /** @return longitude in degrees */
    public double getLongitude() {
        return lonDeg;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "(%.6f, %.6f)", latDeg, lonDeg);
    }
}
