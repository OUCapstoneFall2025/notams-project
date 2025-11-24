package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Simple, explainable prioritizer.
 * Scores by type + keywords + recency + proximity (radiusNm), then sorts desc.
 * Naming:
 *  - W_* constants are scoring WEIGHTS (doubles so we can tune fractionally).
 *  - Integer constants represent natural counts (hours, NM) and are ints.
 */
public class SimplePrioritizer implements NotamPrioritizer {

    // ---- Type weights (doubles so we can fine-tune later) ----
    private static final double W_TYPE_RUNWAY     = 50.0;
    private static final double W_TYPE_TAXIWAY    = 25.0;
    private static final double W_TYPE_AIRSPACE   = 40.0;
    private static final double W_TYPE_OBSTACLE   = 30.0;

    // ---- Keyword weights ----
    private static final double W_KEYWORD_CLOSED           = 40.0;
    private static final double W_KEYWORD_UNSERVICEABLE    = 30.0;
    private static final double W_KEYWORD_MAINTENANCE      = 10.0;

    // ---- Recency & proximity knobs ----
    private static final double W_RECENCY_MAX = 20.0; // full credit if <= 24h old
    private static final int RECENCY_HALF_LIFE_HOURS = 72; // decay after 24h

    private static final double W_RADIUS_NEAR_MAX = 15.0; // full credit if radius <= RADIUS_NEAR_NM
    private static final int RADIUS_NEAR_NM = 5;          // nautical miles

    private final Clock clock;

    /** System clock by default; inject a fixed Clock in tests for determinism. */
    public SimplePrioritizer() { this(Clock.systemUTC()); }

    public SimplePrioritizer(final Clock clock) { this.clock = clock; }

    @Override
    public List<Notam> prioritize(final List<Notam> notams) {
        final List<Notam> copy = new ArrayList<>(notams);
        // Tie-breakers provide stable ordering when scores are equal
        copy.sort(Comparator.<Notam>comparingDouble(this::score).reversed()
                .thenComparing(Notam::getIssued, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Notam::getId, Comparator.nullsLast(String::compareTo)));
        return copy;
    }

    /**
     * Returns the raw priority score for this NOTAM.
     * Higher values indicate higher priority.
     */
    public double score(final Notam n) {
        double s = 0.0;
        s += typeScore(n.getType());
        s += keywordScore(n.getText());
        s += recencyScore(n.getIssued());
        s += proximityScore(n.getRadiusNm());
        return s;
    }

    private double typeScore(final String type) {
        if (type == null) return 0.0;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "RUNWAY", "RWY" -> W_TYPE_RUNWAY;
            case "TAXIWAY", "TWY" -> W_TYPE_TAXIWAY;
            case "AIRSPACE" -> W_TYPE_AIRSPACE;
            case "OBSTACLE" -> W_TYPE_OBSTACLE;
            default -> 0.0;
        };
    }

    private double keywordScore(final String notamText) {
        if (notamText == null) return 0.0;
        final String s = notamText.toUpperCase(Locale.ROOT);
        double sum = 0.0;
        if (s.contains("CLOSED") || s.matches(".*\\bCLSD\\b.*")) sum += W_KEYWORD_CLOSED;
        if (s.contains("UNSERVICEABLE") || s.matches(".*\\bU/S\\b.*")) sum += W_KEYWORD_UNSERVICEABLE;
        if (s.contains("MAINT") || s.contains("MAINTENANCE")) sum += W_KEYWORD_MAINTENANCE;
        return sum;
    }

    private double recencyScore(final OffsetDateTime issued) {
        if (issued == null) return 0.0;
        final long hours = Math.max(0, Duration.between(issued, OffsetDateTime.now(clock)).toHours());
        if (hours <= 24) return W_RECENCY_MAX;
        final double decayHours = (double) (hours - 24);
        final double factor = Math.pow(0.5, decayHours / (double) RECENCY_HALF_LIFE_HOURS);
        return W_RECENCY_MAX * factor;
    }

    private double proximityScore(final Double radiusNm) {
        if (radiusNm == null) return 0.0;
        if (radiusNm <= (double) RADIUS_NEAR_NM) return W_RADIUS_NEAR_MAX;
        // Linear fade from RADIUS_NEAR_NM to 50 NM
        final double capped = Math.min(50.0, Math.max((double) RADIUS_NEAR_NM, radiusNm));
        return W_RADIUS_NEAR_MAX * ((50.0 - capped) / (50.0 - (double) RADIUS_NEAR_NM));
    }
}
