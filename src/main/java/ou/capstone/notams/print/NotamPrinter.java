package ou.capstone.notams.print;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


/**
 * Console printer for NOTAMs.
 *
 * Provides both {@link #print(List)} for CLI stdout and {@link #render(List)}
 * for tests/logging.
 */

public class NotamPrinter {

	public enum TimeMode { UTC_ONLY, LOCAL_ONLY, BOTH }

	private final ZoneId localZoneId;
    private final TimeMode timeMode;
    private final AnsiOutputHelper ansi;


    // Column widths
    private static final int LOCATION_COL_WIDTH       = 4;
    private static final int NUMBER_COL_WIDTH         = 10;
    private static final int CLASSIFICATION_COL_WIDTH = 12;
    private static final int START_TIMESTAMP_COL_WIDTH= 16;
    private static final int END_TIMESTAMP_COL_WIDTH  = 16;
    private static final int SCORE_COL_WIDTH          = 6;
    private static final int CONDITION_COL_WIDTH      = 72;

    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    // Fixed score thresholds for coloring (based on SimplePrioritizer's scale ~0–165)
    private static final double RED_MIN_SCORE    = 100.0; // high priority
    private static final double YELLOW_MIN_SCORE = 50.0;  // medium priority
    // 0–49.999... = low priority
    
    /** Default: both UTC + local time, ANSI colors enabled. */
    public NotamPrinter(final ZoneId localZoneId) {
        this(localZoneId, TimeMode.BOTH, true);
    }
    
    public NotamPrinter(final ZoneId localZoneId,
                        final boolean includeLocalTimes,
                        final boolean enableAnsiColors) {
    	 this(localZoneId, includeLocalTimes ? TimeMode.BOTH : TimeMode.UTC_ONLY, enableAnsiColors);
    }

    
    public NotamPrinter(final ZoneId localZoneId,
                        final TimeMode timeMode,
                        final boolean enableAnsiColors) {
    	   this.localZoneId = localZoneId;
           this.timeMode = timeMode;
           this.ansi = new AnsiOutputHelper(enableAnsiColors);
       }

    /**
     * Prints the formatted NOTAM table to stdout for CLI usage.
     * <p>Note: This delegates to {@link #render(List)} and writes the result to {@code System.out}.</p>
     *
     * @param notams list of NOTAM view models to display; if null/empty, prints an empty-state message.
     */
    public void print(final List<NotamView> notams) {
    	System.out.println(render(notams));
    }

    /**
     * Renders the formatted NOTAM table as a single String.
     * <p>Intended for unit tests and logging—use {@link #print(List)} for interactive CLI output.</p>
     *
     * @param notams list of NOTAM view models; if null/empty, a friendly empty-state string is returned.
     * @return the complete table as a String (including header and rows).
     */
    public String render(final List<NotamView> notams) {
        if (notams == null || notams.isEmpty()) {
            return "No NOTAMs to display.";
        }

        final List<NotamView> sorted = new ArrayList<>(notams);

        sorted.sort(
                Comparator.comparing(NotamView::score,
                                     Comparator.nullsLast(Comparator.reverseOrder()))
                          .thenComparing(NotamView::location,
                                         Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                          .thenComparing(NotamView::startTimeUtc,
                                         Comparator.nullsLast(Comparator.naturalOrder()))
        );

        final StringBuilder sb = new StringBuilder();
        sb.append(buildHeader()).append('\n');
        sb.append(buildSeparator()).append('\n');

        for (final NotamView item : sorted) {
            sb.append(formatRowScoreColored(item)).append('\n');
        }

        return sb.toString();
    }

    // Formatting 
    private String buildHeader() {
        final String startHeaderUtc   = pad("Start(UTC)",  START_TIMESTAMP_COL_WIDTH);
        final String endHeaderUtc     = pad("End(UTC)",    END_TIMESTAMP_COL_WIDTH);
        final String startHeaderLocal = pad("Start(Local)",START_TIMESTAMP_COL_WIDTH);
        final String endHeaderLocal   = pad("End(Local)",  END_TIMESTAMP_COL_WIDTH);

        final String timeHeader = switch (timeMode) {
        case UTC_ONLY   -> startHeaderUtc + "  " + endHeaderUtc;
        case LOCAL_ONLY -> startHeaderLocal + "  " + endHeaderLocal;
        case BOTH       -> startHeaderUtc + "  " + endHeaderUtc;
        default         -> startHeaderUtc + "  " + endHeaderUtc;
    };

        String header = String.format(Locale.ROOT, "%s  %s  %s  %s  %s  %s",
                pad("Loc", LOCATION_COL_WIDTH),
                pad("Number", NUMBER_COL_WIDTH),
                pad("Class", CLASSIFICATION_COL_WIDTH),
                timeHeader,
                pad("Score", SCORE_COL_WIDTH),
                "Condition");

        if (timeMode == TimeMode.BOTH) {
        	  final String locals = startHeaderLocal + "  " + endHeaderLocal;
              header += "\n"
                      + " ".repeat(LOCATION_COL_WIDTH
                                 + NUMBER_COL_WIDTH
                                 + CLASSIFICATION_COL_WIDTH
                                 + 6)
                      + ansi.dim(locals);
        }
        return header;
    }

    private String buildSeparator() {
        final int total =
                LOCATION_COL_WIDTH + 2
              + NUMBER_COL_WIDTH + 2
              + CLASSIFICATION_COL_WIDTH + 2
              + START_TIMESTAMP_COL_WIDTH + 2
              + END_TIMESTAMP_COL_WIDTH + 2
              + SCORE_COL_WIDTH + 2
              + CONDITION_COL_WIDTH;
        return "-".repeat(total);
    }

 // Row formatting with score-based coloring
    private String formatRowScoreColored(final NotamView n) {
        final String location       = pad(clamp(n.location(), LOCATION_COL_WIDTH), LOCATION_COL_WIDTH);
        final String number         = pad(clamp(n.notamNumber(), NUMBER_COL_WIDTH), NUMBER_COL_WIDTH);
        final String classification = pad(clamp(n.classification() == null ? "-" : n.classification(),
                                                CLASSIFICATION_COL_WIDTH),
                                          CLASSIFICATION_COL_WIDTH);

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
        final String coloredCondition ;
        if (scoreValue == null) {
            // No score – leave uncolored 
            coloredCondition = rawCondition;
        } else if (scoreValue >= RED_MIN_SCORE) {
            coloredCondition = ansi.colorRed(rawCondition);    // high priority
        } else if (scoreValue >= YELLOW_MIN_SCORE) {
            coloredCondition = ansi.colorYellow(rawCondition); // medium priority
        } else if (scoreValue >= 0.0) {
            coloredCondition = ansi.colorBlue(rawCondition);
        } else {
            coloredCondition = rawCondition;
        }
        final String timeCols = switch (timeMode) {
        case UTC_ONLY   -> startUtc + "  " + endUtc;
        case LOCAL_ONLY -> startLocal + "  " + endLocal;
        case BOTH       -> startUtc + "  " + endUtc;
        default         -> startUtc + "  " + endUtc;
    };

        String line = String.format("%s  %s  %s  %s  %s  %s",
                location, number, classification, timeCols, scoreText, coloredCondition);

        if (timeMode == TimeMode.BOTH) {
            final String localLine = " ".repeat(LOCATION_COL_WIDTH + NUMBER_COL_WIDTH + CLASSIFICATION_COL_WIDTH + 6)
                    + ansi.dim(startLocal + "  " + endLocal);
            line += "\n" + localLine;
        }

        return line;
    }

    // Tiny helpers 
    private static String pad(final String value, final int width) {
        final String v = (value == null) ? "-" : value;
        if (v.length() >= width) {
            return v;
        }
        final StringBuilder sb = new StringBuilder(v);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String clamp(final String text, final int maxLength) {
        if (text == null) {
            return "-";
        }
        final String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
   
    private static String formatUtc(final Instant instant) {
        return instant == null ? "-" : UTC_FORMATTER.format(instant);
    }

    private String formatLocal(final Instant instant) {
        if (instant == null) {
            return "-";
        }
        return LOCAL_FORMATTER.format(instant.atZone(localZoneId));
    }
}
