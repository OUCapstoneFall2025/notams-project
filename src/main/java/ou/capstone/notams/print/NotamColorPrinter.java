package ou.capstone.notams.print;

import java.time.ZoneId;
import java.util.Locale;

/**
 * Printer that adds ANSI coloring based on NOTAM score.
 */
public final class NotamColorPrinter extends NotamPrinter {

    private final AnsiOutputHelper ansi;

    // Fixed score thresholds for coloring (based on SimplePrioritizer's scale ~0–165)
    private static final double RED_MIN_SCORE    = 100.0; // high priority
    private static final double YELLOW_MIN_SCORE = 50.0;  // medium priority
    // 0–49.999... = low priority

    public NotamColorPrinter(final ZoneId zoneId, final TimeMode timeMode) {
        super(zoneId, timeMode);
        this.ansi = new AnsiOutputHelper(true);
    }

    public NotamColorPrinter(final ZoneId zoneId) {
        this(zoneId, TimeMode.BOTH);
    }

    @Override
    protected String formatRow(final NotamView n) {
        final String location       = pad(clamp(n.location(), LOCATION_COL_WIDTH), LOCATION_COL_WIDTH);
        final String number         = pad(clamp(n.notamNumber(), NUMBER_COL_WIDTH), NUMBER_COL_WIDTH);
        final String classification = pad(
                clamp(n.classification() == null ? "-" : n.classification(), CLASSIFICATION_COL_WIDTH),
                CLASSIFICATION_COL_WIDTH
        );

        final String startUtc   = pad(formatUtc(n.startTimeUtc()),   START_TIMESTAMP_COL_WIDTH);
        final String endUtc     = pad(formatUtc(n.endTimeUtc()),     END_TIMESTAMP_COL_WIDTH);
        final String startLocal = pad(formatLocal(n.startTimeUtc()), START_TIMESTAMP_COL_WIDTH);
        final String endLocal   = pad(formatLocal(n.endTimeUtc()),   END_TIMESTAMP_COL_WIDTH);

        final Double scoreValue = n.score();
        final String scoreText  = pad(
                scoreValue == null ? "-" : String.format(Locale.ROOT, "%.1f", scoreValue),
                SCORE_COL_WIDTH
        );

        final String rawCondition = clamp(n.conditionText(), CONDITION_COL_WIDTH);

        final String coloredCondition;
        if (scoreValue == null) {
            coloredCondition = rawCondition;
        } else if (scoreValue >= RED_MIN_SCORE) {
            coloredCondition = ansi.colorRed(rawCondition);    // high priority
        } else if (scoreValue >= YELLOW_MIN_SCORE) {
            coloredCondition = ansi.colorYellow(rawCondition); // medium priority
        } else if (scoreValue >= 0.0) {
            coloredCondition = ansi.colorBlue(rawCondition);   // low priority (could be green later)
        } else {
            coloredCondition = rawCondition;
        }

        final String timeCols = switch (timeMode) {
            case UTC_ONLY   -> startUtc + "  " + endUtc;
            case LOCAL_ONLY -> startLocal + "  " + endLocal;
            case BOTH       -> startUtc + "  " + endUtc;
        };

        String line = String.format("%s  %s  %s  %s  %s  %s",
                location, number, classification, timeCols, scoreText, coloredCondition);

        if (timeMode == TimeMode.BOTH) {
            final String localLine =
                    " ".repeat(LOCATION_COL_WIDTH + NUMBER_COL_WIDTH + CLASSIFICATION_COL_WIDTH + 6)
                    + ansi.dim(startLocal + "  " + endLocal);
            line += "\n" + localLine;
        }

        return line;
    }
}
