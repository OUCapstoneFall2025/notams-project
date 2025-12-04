package ou.capstone.notams.prioritize;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ou.capstone.notams.Notam;

/**
 * Simple, explainable prioritizer.
 * Scores by type + keywords + recency + proximity (radiusNm), then sorts desc.
 * Naming:
 *  - W_* constants are scoring WEIGHTS (doubles so we can tune fractionally).
 *  - Integer constants represent natural counts (hours, NM) and are ints.
 *
 * CCS-79: Extended with IFR/VFR modes, departure/destination boosting,
 * tower height weighting, NAV aid importance, fuel outages, aerodrome
 * codes (AD 1–3), and deboosting of region-wide NOTAMs.
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

    // ------------------------------------------------------------------
    // CCS-79 extra weights (all additive on top of the original scheme)
    // ------------------------------------------------------------------

    // Departure/destination airports should be higher
    private static final double W_DEPART_DEST_AIRPORT = 60.0;

    // NAV aids very important (especially for IFR)
    private static final double W_NAV_AID_IFR = 70.0;
    private static final double W_NAV_AID_VFR = 30.0;
    private static final String[] NAV_AID_KEYWORDS =
            {"ILS", "VOR", "NDB", "RNAV", "GPS", "GLS", "LOC", "TACAN"};

    // Fuel not available
    private static final double W_FUEL_OUTAGE = 50.0;

    // Aerodrome related to specific airports (codes 1–3)
    private static final double W_AERODROME_CONDITION = 40.0;

    // Flight area obstructions / tall towers
    private static final int TOWER_HEIGHT_THRESHOLD_FT = 200; // anything taller than this starts to score
    private static final double W_TOWER_HEIGHT_PER_FOOT = 0.05;
    private static final double W_TOWER_HEIGHT_MAX = 60.0;
    private static final Pattern HEIGHT_PATTERN =
            Pattern.compile("(\\d{2,5})\\s*(FT|FEET|FT AGL|AGL)\\b", Pattern.CASE_INSENSITIVE);

    // Airspace extra bonus (esp. for IFR)
    private static final double W_AIRSPACE_IFR_BONUS = 20.0;

    // Region-wide NOTAMs (e.g., huge ZFW area) deboost
    private static final double REGION_WIDE_RADIUS_THRESHOLD_NM = 100.0;
    private static final double W_REGION_WIDE_PENALTY = -25.0;

    // Boost for NOTAMs clearly near an airport (already partly captured by radius,
    // but this lets us "pop" them to the top more aggressively when they match
    // departure/destination codes).
    private static final double W_NEAR_AIRPORT_EXTRA = 30.0;

    private final Clock clock;
    private final NotamPrioritizer.Mode mode;
    private final String departureAirport;
    private final String destinationAirport;

    /** System clock by default; inject a fixed Clock in tests for determinism. */
    public SimplePrioritizer() {
        this(null, null, NotamPrioritizer.Mode.IFR, Clock.systemUTC());
    }

    public SimplePrioritizer(final Clock clock) {
        this(null, null, NotamPrioritizer.Mode.IFR, clock);
    }

    /**
     * New constructor for CCS-79:
     * allows the prioritizer to know the departure/destination airports and flight mode.
     */
    public SimplePrioritizer(final String departureAirport,
                             final String destinationAirport,
                             final NotamPrioritizer.Mode mode) {
        this(departureAirport, destinationAirport, mode, Clock.systemUTC());
    }

    /**
     * Full constructor for tests / advanced use.
     */
    public SimplePrioritizer(final String departureAirport,
                             final String destinationAirport,
                             final NotamPrioritizer.Mode mode,
                             final Clock clock) {
        this.departureAirport = departureAirport;
        this.destinationAirport = destinationAirport;
        this.mode = (mode != null) ? mode : NotamPrioritizer.Mode.IFR;
        this.clock = clock;
    }

    @Override
    public List<Notam> prioritize(final List<Notam> notams) {
        final List<Notam> copy = new ArrayList<>(notams);
        // Tie-breakers provide stable ordering when scores are equal
        copy.sort(Comparator.<Notam>comparingDouble(this::score).reversed()
                .thenComparing(Notam::getIssued, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Notam::getId, Comparator.nullsLast(String::compareTo)));
        return copy;
    }

    /* package-private for focused tests */
    double score(final Notam n) {
        double s = 0.0;
        final String text = n.getText();

        // --- Original simple scheme (kept exactly as before) ---
        s += typeScore(n.getType());
        s += keywordScore(text);
        s += recencyScore(n.getIssued());
        s += proximityScore(n.getRadiusNm());

        // --- CCS-79 additions (all additive on top of the original) ---

        // Departure and Destination airports higher priority
        s += departureDestinationBoost(n);

        // NAV aids very important (especially in IFR)
        s += navAidScore(text);

        // Fuel not available
        s += fuelScore(text);

        // Aerodrome conditions (codes 1–3) near airports
        s += aerodromeConditionScore(text);

        // Tall towers / obstructions
        s += towerHeightScore(text);

        // Airspace extra bonus depending on IFR/VFR mode
        s += airspaceModeBonus(n.getType(), text);

        // Region-wide NOTAMs (very large radius) slightly deboosted
        s += regionWidePenalty(n.getRadiusNm());

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

    // ------------------------------------------------------------------
    // CCS-79 helper scoring functions
    // ------------------------------------------------------------------

    /** Boost NOTAMs that are specifically for departure or destination airport. */
    private double departureDestinationBoost(final Notam n) {
        if (departureAirport == null || destinationAirport == null) return 0.0;
        final String loc = n.getLocation();
        if (loc == null) return 0.0;

        final String locUp = loc.toUpperCase(Locale.ROOT);
        final String depUp = departureAirport.toUpperCase(Locale.ROOT);
        final String destUp = destinationAirport.toUpperCase(Locale.ROOT);

        if (locUp.equals(depUp) || locUp.equals(destUp)) {
            double score = W_DEPART_DEST_AIRPORT;
            // If it's also very close in radius, give a bit more nudge.
            final Double r = n.getRadiusNm();
            if (r != null && r <= (double) RADIUS_NEAR_NM) {
                score += W_NEAR_AIRPORT_EXTRA;
            }
            return score;
        }
        return 0.0;
    }

    /** NAV aids like ILS/VOR/RNAV outages. */
    private double navAidScore(final String text) {
        if (text == null) return 0.0;
        final String s = text.toUpperCase(Locale.ROOT);

        boolean contains = false;
        for (final String kw : NAV_AID_KEYWORDS) {
            if (s.contains(kw)) {
                contains = true;
                break;
            }
        }
        if (!contains) return 0.0;

        if (mode == NotamPrioritizer.Mode.IFR) {
            return W_NAV_AID_IFR;
        } else {
            // Still relevant to VFR, but slightly less critical
            return W_NAV_AID_VFR;
        }
    }

    /** Fuel outages at the airport. */
    private double fuelScore(final String text) {
        if (text == null) return 0.0;
        final String s = text.toUpperCase(Locale.ROOT);

        // Quickly bail if we don't see any fuel-related words
        if (!s.contains("FUEL") && !s.contains("AVGAS") && !s.contains("JET A") && !s.contains("JETA")) {
            return 0.0;
        }

        // Look for outage/unavailability words
        if (s.contains("UNAVAILABLE") ||
            s.contains("NOT AVAILABLE") ||
            s.contains("NOT AVBL") ||
            s.contains("NIL")) {
            return W_FUEL_OUTAGE;
        }

        return 0.0;
    }

    /** Aerodrome codes AD 1/2/3 etc. often signal airport condition NOTAMs. */
    private double aerodromeConditionScore(final String text) {
        if (text == null) return 0.0;
        final String s = text.toUpperCase(Locale.ROOT);
        if (s.matches(".*\\bAD\\s*[1-3]\\b.*")) {
            return W_AERODROME_CONDITION;
        }
        return 0.0;
    }

    /** Tall towers / obstructions: higher structures are more important, esp. for VFR. */
    private double towerHeightScore(final String text) {
        if (text == null) return 0.0;
        final Matcher matcher = HEIGHT_PATTERN.matcher(text);
        int maxHeight = 0;

        while (matcher.find()) {
            try {
                final int h = Integer.parseInt(matcher.group(1));
                if (h > maxHeight) {
                    maxHeight = h;
                }
            } catch (final NumberFormatException ignore) {
                // Ignore bad height values and continue scanning
            }
        }

        if (maxHeight <= TOWER_HEIGHT_THRESHOLD_FT) {
            return 0.0;
        }

        double extra = (maxHeight - TOWER_HEIGHT_THRESHOLD_FT) * W_TOWER_HEIGHT_PER_FOOT;
        double score = Math.min(W_TOWER_HEIGHT_MAX, extra);

        // For VFR, tall towers matter even more
        if (mode == NotamPrioritizer.Mode.VFR) {
            score *= 1.3;
        }

        return score;
    }

     /** Extra boost for airspace restrictions/TFR depending on flight rules. */
    private double airspaceModeBonus(final String type, final String text) {
        if (text == null) return 0.0;

        final String s = text.toUpperCase(Locale.ROOT);

    // Only give the extra bonus when the NOTAM text explicitly calls out
    // an airspace restriction / TFR. This keeps "plain" AIRSPACE NOTAMs
    // with neutral text below comparable RUNWAY NOTAMs, as the tests expect.
        if (!s.contains("AIRSPACE") && !s.contains("TFR")) {
            return 0.0;
    }

        if (mode == NotamPrioritizer.Mode.IFR) {
            return W_AIRSPACE_IFR_BONUS;
    } else {
            return W_AIRSPACE_IFR_BONUS * 0.6;

        }
}


    /** Region-wide NOTAMs (very large radius) get a small negative adjustment. */
    private double regionWidePenalty(final Double radiusNm) {
        if (radiusNm == null) return 0.0;
        if (radiusNm >= REGION_WIDE_RADIUS_THRESHOLD_NM) {
            return W_REGION_WIDE_PENALTY;
        }
        return 0.0;
    }
}