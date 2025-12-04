package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;

/**
 * Scores proximity information: radius & whether the NOTAM is at the
 * departure or destination airport. Also adds a slight penalty for
 * region-wide NOTAMs with very large radii.
 */
public final class ProximityScorer implements NotamScorer {

    // ---- Proximity knobs ----
    private static final double W_RADIUS_NEAR_MAX = 15.0; // full credit if radius <= RADIUS_NEAR_NM
    private static final int RADIUS_NEAR_NM = 5;          // nautical miles

    // Boost for NOTAMs explicitly at departure / destination
    private static final double W_DEPART_DESTINATION = 20.0;

    // Large "region" NOTAM penalty
    private static final int REGION_WIDE_RADIUS_NM = 100;
    private static final double W_REGION_WIDE_PENALTY = -10.0;

    private final String departureAirport;
    private final String destinationAirport;

    public ProximityScorer(final String departureAirport,
                           final String destinationAirport) {
        this.departureAirport = (departureAirport != null)
                ? departureAirport.toUpperCase()
                : null;
        this.destinationAirport = (destinationAirport != null)
                ? destinationAirport.toUpperCase()
                : null;
    }

    @Override
    public double score(final Notam notam) {
        double s = 0.0;

        final Double radiusNm = notam.getRadiusNm();
        if (radiusNm != null) {
            if (radiusNm <= (double) RADIUS_NEAR_NM) {
                // Very local NOTAM -> full credit
                s += W_RADIUS_NEAR_MAX;
            } else {
                // Linear fade out from RADIUS_NEAR_NM to 50NM.
                final double capped =
                        Math.min(50.0, Math.max((double) RADIUS_NEAR_NM, radiusNm));
                final double factor =
                        (50.0 - capped) / (50.0 - (double) RADIUS_NEAR_NM);
                s += W_RADIUS_NEAR_MAX * factor;
            }

            // Region-wide NOTAMs
            if (radiusNm >= (double) REGION_WIDE_RADIUS_NM) {
                s += W_REGION_WIDE_PENALTY;
            }
        }

        // Departure / destination specific NOTAMs get a bonus
        final String loc = notam.getLocation();
        if (loc != null) {
            final String upper = loc.toUpperCase();
            if (departureAirport != null && upper.equals(departureAirport)) {
                s += W_DEPART_DESTINATION;
            }
            if (destinationAirport != null && upper.equals(destinationAirport)) {
                s += W_DEPART_DESTINATION;
            }
        }

        return s;
    }
}