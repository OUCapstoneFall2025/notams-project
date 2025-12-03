package ou.capstone.notams.print;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * Console printer for NOTAMs (no ANSI colors).
 *
 * Subclasses customize how each NOTAM row is styled (colors, emphasis, etc.)
 * via {@link #decorateConditionLine(NotamView, String, Double)} and
 * {@link #decorateLocalTimeRow(NotamView, String)}.
 * 
 * Provides both {@link #print(List)} for CLI stdout and {@link #render(List)}
 * for tests/logging.
 */
public abstract class NotamPrinter {

    public enum TimeMode { UTC_ONLY, LOCAL_ONLY, BOTH }

    protected final ZoneId localZoneId;
    protected final TimeMode timeMode;

    protected static final int LOCATION_COL_WIDTH        = 4;
    protected static final int NUMBER_COL_WIDTH          = 10;
    protected static final int TYPE_COL_WIDTH            = 12;
    protected static final int START_TIMESTAMP_COL_WIDTH = 16;
    protected static final int END_TIMESTAMP_COL_WIDTH   = 16;
    protected static final int SCORE_COL_WIDTH           = 6;
    protected static final int CONDITION_COL_WIDTH       = 72;

    protected static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);
    protected static final DateTimeFormatter LOCAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    /** Default: both UTC + local time */
    public NotamPrinter(final ZoneId localZoneId) {
        this(localZoneId, TimeMode.BOTH);
    }

    public NotamPrinter(final ZoneId localZoneId,
                        final boolean includeLocalTimes) {
        this(localZoneId, includeLocalTimes ? TimeMode.BOTH : TimeMode.UTC_ONLY);
    }

    public NotamPrinter(final ZoneId localZoneId,
                        final TimeMode timeMode) {
        this.localZoneId = localZoneId;
        this.timeMode = timeMode;
    }

    /**
     * Print directly to stdout for CLI usage.
     * Delegates to {@link #render(List)}.
     */
    public void print(final List<NotamView> notams) {
        System.out.println(render(notams));
    }

    /**
     * Renders the formatted NOTAM table as a single String.
     * Intended for unit tests and logging – CLI code should use {@link #print(List)}.
     *
     * @param notams list of NOTAM view models; if null/empty, a friendly empty-state string is returned.
     * @return the complete table as a String (including header and rows).
     */
    public String render(final List<NotamView> notams) {
        if (notams == null || notams.isEmpty()) {
            return "No NOTAMs to display.";
        }

        final List<NotamView> sorted = new ArrayList<>(notams);

        // Sort primarily by score (highest first), then by location, then by start time
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
            sb.append(formatRow(item)).append('\n');
        }

        return sb.toString();
    }
    // Hooks for subclasses
    
    // Customize each wrapped NOTAM text line.
    protected abstract String decorateConditionLine(
            NotamView n,
            String line,
            Double score
    );
    
    // Customize the local time row when TimeMode == BOTH.
    protected String decorateLocalTimeRow(
            final NotamView n,
            final String localTimes
    ) {
        return localTimes;
    }
    
    // Header & separators

    private String buildHeader() {
        final String startHeaderUtc   = pad("Start(UTC)",  START_TIMESTAMP_COL_WIDTH);
        final String endHeaderUtc     = pad("End(UTC)",    END_TIMESTAMP_COL_WIDTH);
        final String startHeaderLocal = pad("Start(Local)",START_TIMESTAMP_COL_WIDTH);
        final String endHeaderLocal   = pad("End(Local)",  END_TIMESTAMP_COL_WIDTH);

        final String timeHeader = switch (timeMode) {
            case UTC_ONLY   -> startHeaderUtc + "  " + endHeaderUtc;
            case LOCAL_ONLY -> startHeaderLocal + "  " + endHeaderLocal;
            case BOTH       -> startHeaderUtc + "  " + endHeaderUtc;
        };

        String header = String.format(Locale.ROOT, "%s  %s  %s  %s  %s  %s",
                pad("Loc", LOCATION_COL_WIDTH),
                pad("Number", NUMBER_COL_WIDTH),
                pad("Type", TYPE_COL_WIDTH),
                timeHeader,
                pad("Score", SCORE_COL_WIDTH),
                "NOTAM Text");

        if (timeMode == TimeMode.BOTH) {
            final String locals = startHeaderLocal + "  " + endHeaderLocal;
            header += "\n"
                    + " ".repeat(LOCATION_COL_WIDTH
                    + NUMBER_COL_WIDTH
                    + TYPE_COL_WIDTH
                    + 6)
                    + locals;
        }
        return header;
    }

    private String buildSeparator() {
        final int total =
                LOCATION_COL_WIDTH + 2
                        + NUMBER_COL_WIDTH + 2
                        + TYPE_COL_WIDTH + 2
                        + START_TIMESTAMP_COL_WIDTH + 2
                        + END_TIMESTAMP_COL_WIDTH + 2
                        + SCORE_COL_WIDTH + 2
                        + CONDITION_COL_WIDTH;
        return "-".repeat(total);
    }

    /**
     * Base row formatter: displays NOTAMs in traditional format with text wrapping.
     * {@link NotamColorPrinter} overrides this to add colors.
     */
    protected final String formatRow(final NotamView n) {
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

        // Calculate indent for wrapped text lines
        final int conditionIndent = LOCATION_COL_WIDTH + 2
                + NUMBER_COL_WIDTH + 2
                + TYPE_COL_WIDTH + 2
                + START_TIMESTAMP_COL_WIDTH + 2
                + END_TIMESTAMP_COL_WIDTH + 2
                + SCORE_COL_WIDTH + 2;

        // Format in traditional NOTAM format and wrap text (no truncation)
        final String traditionalNotam = n.formatTraditionalNotam();
        final String wrappedCondition = wrapText(traditionalNotam);
        final String[] lines = wrappedCondition.split("\n");
        final StringBuilder formattedCondition = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                formattedCondition.append('\n').append(" ".repeat(conditionIndent));
            }
            final String decorated = decorateConditionLine(n, lines[i], scoreValue);
            formattedCondition.append(decorated);
        }

        final String timeCols = switch (timeMode) {
            case UTC_ONLY   -> startUtc + "  " + endUtc;
            case LOCAL_ONLY -> startLocal + "  " + endLocal;
            case BOTH       -> startUtc + "  " + endUtc;
        };

        String line = String.format("%s  %s  %s  %s  %s  %s",
                location, number, classification, timeCols, scoreText, formattedCondition);

        if (timeMode == TimeMode.BOTH) {
            final String localTimes = startLocal + "  " + endLocal;
            final String decoratedLocalTimes = decorateLocalTimeRow(n, localTimes);
            final String localLine =
            " ".repeat(LOCATION_COL_WIDTH + NUMBER_COL_WIDTH + TYPE_COL_WIDTH + 6)
                            + decoratedLocalTimes;
            line += "\n" + localLine;
        }

        return line;
    }

    // Helper methods

    protected static String pad(final String value, final int width) {
        final String v = (value == null) ? "-" : value;
        return StringUtils.rightPad(v, width);
    }

    protected static String clamp(final String text, final int maxLength) {
        if (text == null) {
            return "-";
        }
        final String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return StringUtils.abbreviate(normalized, maxLength);
    }

    protected static String formatUtc(final Instant instant) {
        return instant == null ? "-" : UTC_FORMATTER.format(instant);
    }

    protected String formatLocal(final Instant instant) {
        if (instant == null) {
            return "-";
        }
        return LOCAL_FORMATTER.format(instant.atZone(localZoneId));
    }

    /**
     * Wraps text to multiple lines for the condition column.
     * Tries to break at spaces, but will break long words if necessary.
     * No truncation - full NOTAM content is always displayed.
     *
     * @param text the text to wrap
     * @return the wrapped text with newlines
     */
    protected static String wrapText(final String text) {
        if (text == null || text.length() <= CONDITION_COL_WIDTH) {
            return text;
        }

        final StringBuilder result = new StringBuilder();
        final String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            int potentialLength = currentLine.length() + (!currentLine.isEmpty() ? 1 : 0) + word.length();

            if (potentialLength > CONDITION_COL_WIDTH && !currentLine.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append('\n');
                }
                result.append(currentLine);
                currentLine = new StringBuilder();
            }

            if (word.length() > CONDITION_COL_WIDTH) {
                // Handle words longer than column width
                if (!currentLine.isEmpty()) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(currentLine);
                    currentLine = new StringBuilder();
                }

                // Break long word across multiple lines
                for (int i = 0; i < word.length(); i += CONDITION_COL_WIDTH) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(word, i, Math.min(i + CONDITION_COL_WIDTH, word.length()));
                }
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(' ');
                }
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(currentLine);
        }

        return result.toString();
    }
}