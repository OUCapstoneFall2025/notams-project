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
    protected String decorateConditionLine(
            final NotamView n,
            final String line,
            final Double scoreValue
    ) {
        if (scoreValue == null) {
            return line;
        } else if (scoreValue >= RED_MIN_SCORE) {
            return ansi.colorRed(line);
        } else if (scoreValue >= YELLOW_MIN_SCORE) {
            return ansi.colorYellow(line);
        } else if (scoreValue >= 0.0) {
            return ansi.colorBlue(line);
        } else {
            return line;
        }
    }

    @Override
    protected String decorateLocalTimeRow(
            final NotamView n,
            final String localTimes
    ) {
        // Dims the local-time row when BOTH times are shown
        return ansi.dim(localTimes);
    }
}