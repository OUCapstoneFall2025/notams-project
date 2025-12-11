package ou.capstone.notams.prioritize;

import java.util.Locale;
import java.util.regex.Pattern;

import ou.capstone.notams.Notam;

/**
 * Scores NOTAMs based on simple patterns:
 *   - Type (RUNWAY, TAXIWAY, AIRSPACE, OBSTACLE)
 *   - Critical keywords (CLOSED, U/S, MAINT, NAVAIDs, fuel not available)
 *   - Special airspace patterns (UAS, GLD, HIGH SPEED)
 */
public final class PatternMatchingScorer implements NotamScorer {

    // ---- Type weights ----
    private static final double W_TYPE_RUNWAY   = 50.0;
    private static final double W_TYPE_TAXIWAY  = 25.0;
    private static final double W_TYPE_AIRSPACE = 40.0;
    private static final double W_TYPE_OBSTACLE = 30.0;

    // ---- Keyword weights ----
    private static final double W_KEYWORD_CLOSED        = 40.0;
    private static final double W_KEYWORD_UNSERVICEABLE = 30.0;
    private static final double W_KEYWORD_MAINT         = 10.0;

    // Navigation aids, very important for both IFR and VFR
    private static final double W_KEYWORD_NAVAID        = 35.0;

    // Fuel not available
    private static final double W_KEYWORD_FUEL_NA       = 35.0;

    // Special airspace patterns
    private static final double W_KEYWORD_UAS           = 25.0;
    private static final double W_KEYWORD_GLD           = 20.0;
    private static final double W_KEYWORD_HIGH_SPEED    = 25.0;

    // ---- Regex patterns ----

    private static final Pattern CLOSED =
            Pattern.compile("\\b(CLOSED|CLSD)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern UNSERVICEABLE =
            Pattern.compile("\\b(UNSERVICEABLE|U/S)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MAINT =
            Pattern.compile("\\b(MAINT|MAINTENANCE)\\b", Pattern.CASE_INSENSITIVE);

    // NAV aids: GPS, VOR, NDB, ILS, LOC, GLS, etc.
    private static final Pattern NAVAID =
            Pattern.compile("\\b(NAVAID|VOR/DME?|VOR\\b|NDB\\b|ILS\\b|LOC\\b|GPS\\b|GLS\\b)",
                    Pattern.CASE_INSENSITIVE);

    // Fuel not available (handles: FUEL NOT AVBL, FUEL NOT AVAILABLE, etc.)
    private static final Pattern FUEL_NOT_AVAILABLE =
            Pattern.compile("\\bFUEL\\b.*\\bNOT\\s+AV(?:AIL(?:ABLE)?|BL)\\b",
                    Pattern.CASE_INSENSITIVE);

    // UAS / unmanned / drones
    private static final Pattern UAS =
            Pattern.compile("\\b(UAS|UNMANNED|DRONE)\\b", Pattern.CASE_INSENSITIVE);

    // GLD / gliders
    private static final Pattern GLIDER =
            Pattern.compile("\\b(GLD|GLIDER)\\b", Pattern.CASE_INSENSITIVE);

    // High-speed / high speed / HIGHSPD / HI-SPD
    private static final Pattern HIGH_SPEED =
            Pattern.compile("\\b(HIGH\\s*SPEED|HIGHSPD|HI-SPD)\\b",
                    Pattern.CASE_INSENSITIVE);

    // Flight mode is stored
    private final NotamPrioritizer.Mode mode;

    public PatternMatchingScorer(final NotamPrioritizer.Mode mode) {
        this.mode = (mode != null) ? mode : NotamPrioritizer.Mode.IFR;
    }

    public PatternMatchingScorer() {
        this(NotamPrioritizer.Mode.IFR);
    }

    @Override
    public double score(final Notam n) {
        if (n == null) {
            return 0.0;
        }
        double score = 0.0;
        score += typeScore(n.getType());
        score += keywordScore(n.getText());
        return score;
    }

    private double typeScore(final String type) {
        if (type == null) {
            return 0.0;
        }
        final String upper = type.toUpperCase(Locale.ROOT);

        return switch (upper) {
            case "RUNWAY", "RWY"   -> W_TYPE_RUNWAY;
            case "TAXIWAY", "TWY"  -> W_TYPE_TAXIWAY;
            case "AIRSPACE"        -> W_TYPE_AIRSPACE;
            case "OBSTACLE"        -> W_TYPE_OBSTACLE;
            default                -> 0.0;
        };
    }

    private double keywordScore(final String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        if (CLOSED.matcher(text).find()) {
            score += W_KEYWORD_CLOSED;
        }
        if (UNSERVICEABLE.matcher(text).find()) {
            score += W_KEYWORD_UNSERVICEABLE;
        }
        if (MAINT.matcher(text).find()) {
            score += W_KEYWORD_MAINT;
        }
        if (NAVAID.matcher(text).find()) {
            score += W_KEYWORD_NAVAID;
        }
        if (FUEL_NOT_AVAILABLE.matcher(text).find()) {
            score += W_KEYWORD_FUEL_NA;
        }
        if (UAS.matcher(text).find()) {
            score += W_KEYWORD_UAS;
        }
        if (GLIDER.matcher(text).find()) {
            score += W_KEYWORD_GLD;
        }
        if (HIGH_SPEED.matcher(text).find()) {
            score += W_KEYWORD_HIGH_SPEED;
        }

        return score;
    }
}