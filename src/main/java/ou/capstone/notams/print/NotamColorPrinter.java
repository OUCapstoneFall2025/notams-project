package ou.capstone.notams.print;

import java.time.ZoneId;
import java.util.Locale;

/**
 * Printer that adds ANSI coloring based on NOTAM score.
 */
public final class NotamColorPrinter extends NotamPrinter {

    private final AnsiOutputHelper ansi;

    // Fixed score thresholds for coloring (based on SimplePrioritizer's scale ~0–165)
    private static final double RED_MIN_SCORE    = 100.0;
    private static final double YELLOW_MIN_SCORE = 50.0;
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
                clamp(n.classification() == null ? "-" : n.classification(), TYPE_COL_WIDTH),
                TYPE_COL_WIDTH
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

        final int conditionIndent = LOCATION_COL_WIDTH + 2
                + NUMBER_COL_WIDTH + 2
                + TYPE_COL_WIDTH + 2
                + START_TIMESTAMP_COL_WIDTH + 2
                + END_TIMESTAMP_COL_WIDTH + 2
                + SCORE_COL_WIDTH + 2;

        final String traditionalNotam = n.formatTraditionalNotam();
        final String wrappedCondition = wrapText(traditionalNotam);
        final String[] lines = wrappedCondition.split("\n");
        final StringBuilder formattedCondition = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                formattedCondition.append('\n').append(" ".repeat(conditionIndent));
            }

            // Apply color to each line based on score
            final String coloredLine;
            if (scoreValue == null) {
                coloredLine = lines[i];
            } else if (scoreValue >= RED_MIN_SCORE) {
                coloredLine = ansi.colorRed(lines[i]);    // high priority
            } else if (scoreValue >= YELLOW_MIN_SCORE) {
                coloredLine = ansi.colorYellow(lines[i]); // medium priority
            } else if (scoreValue >= 0.0) {
                coloredLine = ansi.colorBlue(lines[i]);   // low priority
            } else {
                coloredLine = lines[i];
            }

            formattedCondition.append(coloredLine);
        }

        final String timeCols = switch (timeMode) {
            case UTC_ONLY   -> startUtc + "  " + endUtc;
            case LOCAL_ONLY -> startLocal + "  " + endLocal;
            case BOTH       -> startUtc + "  " + endUtc;
        };

        String line = String.format("%s  %s  %s  %s  %s  %s",
                location, number, classification, timeCols, scoreText, formattedCondition);

        if (timeMode == TimeMode.BOTH) {
            final String localLine =
                    " ".repeat(LOCATION_COL_WIDTH + NUMBER_COL_WIDTH + TYPE_COL_WIDTH + 6)
                            + ansi.dim(startLocal + "  " + endLocal);
            line += "\n" + localLine;
        }

        return line;
    }
}