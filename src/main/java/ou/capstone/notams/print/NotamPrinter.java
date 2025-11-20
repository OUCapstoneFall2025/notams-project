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
 * Renders NOTAMs as a copy-friendly console table.
 * <p>
 * Design choices:
 * <ul>
 *   <li>Uses {@code System.out} so output has no log prefixes and ANSI colors render cleanly.</li>
 *   <li>Provides {@link #render(List)} for tests/logging and {@link #print(List)} for the CLI.</li>
 *   <li>Coloring is derived from prioritizer <b>rank bands</b> (top % red, next % yellow, rest blue).</li>
 *   <li>Time columns shown as UTC only, local only, or both via {@link TimeMode}.</li>
 * </ul>
 */

public class NotamPrinter {

	public enum TimeMode { UTC_ONLY, LOCAL_ONLY, BOTH }

	private final ZoneId localZoneId;
    private final TimeMode timeMode;
    private final AnsiOutputHelper ansi;


    // Rank band percentages
    private final double redTopPercent;
    private final double yellowNextPercent;

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

    public NotamPrinter(final ZoneId localZoneId,
                        final boolean includeLocalTimes,
                        final boolean enableAnsiColors) {
    	 this(localZoneId, includeLocalTimes ? TimeMode.BOTH : TimeMode.UTC_ONLY, enableAnsiColors, 0.10, 0.20);
    }

    /** Customizable band percentages. */
    public NotamPrinter(final ZoneId localZoneId,
                        final TimeMode timeMode,
                        final boolean enableAnsiColors,
                        final double redTopPercent,
                        final double yellowNextPercent) {
    	   this.localZoneId = localZoneId;
           this.timeMode = timeMode;
           this.ansi = new AnsiOutputHelper(enableAnsiColors);
           this.redTopPercent = clampPercent(redTopPercent);
           this.yellowNextPercent = clampPercent(yellowNextPercent);
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
     * Prints the formatted NOTAM table to stdout for CLI usage with output configuration.
     *
     * @param notams list of NOTAM view models to display; if null/empty, prints an empty-state message.
     * @param config output configuration for formatting options
     */
    public void print(final List<NotamView> notams, final OutputConfig config) {
    	System.out.println(render(notams, config));
    }

    /**
     * Renders the formatted NOTAM table as a single String.
     * <p>Intended for unit tests and logging—use {@link #print(List)} for interactive CLI output.</p>
     *
     * @param notams list of NOTAM view models; if null/empty, a friendly empty-state string is returned.
     * @return the complete table as a String (including header and rows).
     */
    public String render(final List<NotamView> notams) {
        return render(notams, OutputConfig.defaults());
    }

    /**
     * Renders the formatted NOTAM table as a single String with output configuration.
     * <p>Intended for unit tests and logging—use {@link #print(List, OutputConfig)} for interactive CLI output.</p>
     *
     * @param notams list of NOTAM view models; if null/empty, a friendly empty-state string is returned.
     * @param config output configuration for formatting options
     * @return the complete table as a String (including header and rows).
     */
    public String render(final List<NotamView> notams, final OutputConfig config) {
        if (notams == null || notams.isEmpty()) {
            return "No NOTAMs to display.";
        }

        final List<NotamView> sorted = new ArrayList<>(notams);

        final Comparator<NotamView> byScoreDesc =
                Comparator.comparing(NotamView::score, Comparator.nullsLast(Comparator.naturalOrder()))
                          .reversed();

        sorted.sort(
                Comparator.comparing(NotamView::location, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(byScoreDesc)
                        .thenComparing(NotamView::startTimeUtc, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        // Compute rank cutoffs from percentages
        final int total = sorted.size();
        final int colorRedIndexCutoff = Math.max(0, Math.min(total, (int) Math.ceil(total * redTopPercent)));
        final int colorYellowIndexCutoff = Math.max(
                colorRedIndexCutoff,
                Math.min(total, colorRedIndexCutoff + (int) Math.ceil(total * yellowNextPercent))
        );

        final StringBuilder sb = new StringBuilder();
        
        if (config.separateMetadata()) {
            // Separated metadata mode: show metadata section, then text section
            sb.append(buildMetadataHeader()).append('\n');
            sb.append(buildMetadataSeparator()).append('\n');
            
            for (int i = 0; i < sorted.size(); i++) {
                final NotamView item = sorted.get(i);
                if (config.showDelimiters() && i > 0) {
                    sb.append(buildDelimiter()).append('\n');
                }
                sb.append(formatMetadataRow(item)).append('\n');
            }
            
            sb.append('\n');
            sb.append(buildTextHeader()).append('\n');
            sb.append(buildTextSeparator()).append('\n');
            
            for (int i = 0; i < sorted.size(); i++) {
                final NotamView item = sorted.get(i);
                if (config.showDelimiters() && i > 0) {
                    sb.append(buildDelimiter()).append('\n');
                }
                sb.append(formatTextRow(item, i, colorRedIndexCutoff, colorYellowIndexCutoff, config)).append('\n');
            }
        } else {
            // Standard table mode
            sb.append(buildHeader()).append('\n');
            sb.append(buildSeparator()).append('\n');

            for (int i = 0; i < sorted.size(); i++) {
                final NotamView item = sorted.get(i);
                if (config.showDelimiters() && i > 0) {
                    sb.append(buildDelimiter()).append('\n');
                }
                sb.append(formatRowRankColored(item, i, colorRedIndexCutoff, colorYellowIndexCutoff, config)).append('\n');
            }
        }

        return sb.toString();
    }

    private String buildHeader() {
        final String startHeaderUtc   = pad("Start(UTC)",  START_TIMESTAMP_COL_WIDTH);
        final String endHeaderUtc     = pad("End(UTC)",    END_TIMESTAMP_COL_WIDTH);
        final String startHeaderLocal = pad("Start(Local)",START_TIMESTAMP_COL_WIDTH);
        final String endHeaderLocal   = pad("End(Local)",  END_TIMESTAMP_COL_WIDTH);

        final String timeHeader = buildTimeColumns(startHeaderUtc, endHeaderUtc, startHeaderLocal, endHeaderLocal);

        String header = String.format(Locale.ROOT, "%s  %s  %s  %s  %s  %s",
                pad("Loc", LOCATION_COL_WIDTH),
                pad("Number", NUMBER_COL_WIDTH),
                pad("Type", CLASSIFICATION_COL_WIDTH),
                timeHeader,
                pad("Score", SCORE_COL_WIDTH),
                "NOTAM Text");

        return addLocalTimeRow(header, startHeaderLocal, endHeaderLocal);
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

    private String formatRowRankColored(final NotamView n,
                                        final int index,
                                        final int colorRedIndexCutoff,
                                        final int colorYellowIndexCutoff,
                                        final OutputConfig config) {

        final String location       = pad(clamp(n.location(), LOCATION_COL_WIDTH), LOCATION_COL_WIDTH);
        final String number         = pad(clamp(n.notamNumber(), NUMBER_COL_WIDTH), NUMBER_COL_WIDTH);
        final String classification = pad(clamp(n.classification() == null ? "-" : n.classification(),
                                                CLASSIFICATION_COL_WIDTH),
                                          CLASSIFICATION_COL_WIDTH);

        final String startUtc   = pad(formatUtc(n.startTimeUtc()),   START_TIMESTAMP_COL_WIDTH);
        final String endUtc     = pad(formatUtc(n.endTimeUtc()),     END_TIMESTAMP_COL_WIDTH);
        final String startLocal = pad(formatLocal(n.startTimeUtc()), START_TIMESTAMP_COL_WIDTH);
        final String endLocal   = pad(formatLocal(n.endTimeUtc()),   END_TIMESTAMP_COL_WIDTH);

        final String score = pad(n.score() == null ? "-" : String.format(Locale.ROOT, "%.2f", n.score()),
                                 SCORE_COL_WIDTH);

        // Calculate indent
        final int conditionIndent = LOCATION_COL_WIDTH + 2
                + NUMBER_COL_WIDTH + 2
                + CLASSIFICATION_COL_WIDTH + 2
                + START_TIMESTAMP_COL_WIDTH + 2
                + END_TIMESTAMP_COL_WIDTH + 2
                + SCORE_COL_WIDTH + 2;

        final String traditionalNotam = applyTruncation(formatTraditionalNotam(n), config);
        final String wrappedCondition = wrapText(traditionalNotam);
        final String[] lines = wrappedCondition.split("\n");
        final StringBuilder formattedCondition = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                formattedCondition.append('\n').append(" ".repeat(conditionIndent));
            }
            final String coloredLine = (index < colorRedIndexCutoff)    ? ansi.colorRed(lines[i])
                    : (index < colorYellowIndexCutoff) ? ansi.colorYellow(lines[i])
                    : ansi.colorBlue(lines[i]);
            formattedCondition.append(coloredLine);
        }

        final String timeCols = buildTimeColumns(startUtc, endUtc, startLocal, endLocal);

        String line = String.format("%s  %s  %s  %s  %s  %s",
                location, number, classification, timeCols, score, formattedCondition);

        return addLocalTimeRow(line, startLocal, endLocal);
    }

    private String buildMetadataHeader() {
        final String startHeaderUtc   = pad("Start(UTC)",  START_TIMESTAMP_COL_WIDTH);
        final String endHeaderUtc     = pad("End(UTC)",    END_TIMESTAMP_COL_WIDTH);
        final String startHeaderLocal = pad("Start(Local)",START_TIMESTAMP_COL_WIDTH);
        final String endHeaderLocal   = pad("End(Local)",  END_TIMESTAMP_COL_WIDTH);

        final String timeHeader = buildTimeColumns(startHeaderUtc, endHeaderUtc, startHeaderLocal, endHeaderLocal);

        String header = String.format(Locale.ROOT, "%s  %s  %s  %s  %s",
                pad("Loc", LOCATION_COL_WIDTH),
                pad("Number", NUMBER_COL_WIDTH),
                pad("Type", CLASSIFICATION_COL_WIDTH),
                timeHeader,
                pad("Score", SCORE_COL_WIDTH));

        header = addLocalTimeRow(header, startHeaderLocal, endHeaderLocal);
        return "Metadata:\n" + header;
    }

    private String buildMetadataSeparator() {
        final int total =
                LOCATION_COL_WIDTH + 2
              + NUMBER_COL_WIDTH + 2
              + CLASSIFICATION_COL_WIDTH + 2
              + START_TIMESTAMP_COL_WIDTH + 2
              + END_TIMESTAMP_COL_WIDTH + 2
              + SCORE_COL_WIDTH + 2;
        return "-".repeat(total);
    }

    private String buildTextHeader() {
        return "NOTAM Text:";
    }

    private String buildTextSeparator() {
        return "-".repeat(80);
    }

    private String formatMetadataRow(final NotamView n) {
        final String location       = pad(clamp(n.location(), LOCATION_COL_WIDTH), LOCATION_COL_WIDTH);
        final String number         = pad(clamp(n.notamNumber(), NUMBER_COL_WIDTH), NUMBER_COL_WIDTH);
        final String classification = pad(clamp(n.classification() == null ? "-" : n.classification(),
                                                CLASSIFICATION_COL_WIDTH),
                                          CLASSIFICATION_COL_WIDTH);

        final String startUtc   = pad(formatUtc(n.startTimeUtc()),   START_TIMESTAMP_COL_WIDTH);
        final String endUtc     = pad(formatUtc(n.endTimeUtc()),     END_TIMESTAMP_COL_WIDTH);
        final String startLocal = pad(formatLocal(n.startTimeUtc()), START_TIMESTAMP_COL_WIDTH);
        final String endLocal   = pad(formatLocal(n.endTimeUtc()),   END_TIMESTAMP_COL_WIDTH);

        final String score = pad(n.score() == null ? "-" : String.format(Locale.ROOT, "%.2f", n.score()),
                                 SCORE_COL_WIDTH);

        final String timeCols = buildTimeColumns(startUtc, endUtc, startLocal, endLocal);

        String line = String.format("%s  %s  %s  %s  %s",
                location, number, classification, timeCols, score);

        return addLocalTimeRow(line, startLocal, endLocal);
    }

    private String formatTextRow(final NotamView n,
                                 final int index,
                                 final int colorRedIndexCutoff,
                                 final int colorYellowIndexCutoff,
                                 final OutputConfig config) {
        final String rawText = formatTraditionalNotam(n);
        final String processedText = applyTruncation(rawText, config);

        return (index < colorRedIndexCutoff)    ? ansi.colorRed(processedText)
                : (index < colorYellowIndexCutoff) ? ansi.colorYellow(processedText)
                : ansi.colorBlue(processedText);
    }

    private String buildDelimiter() {
        return "-".repeat(80);
    }

    // Tiny helpers
    private static String pad(final String value, final int width) {
        final String v = value == null ? "-" : value;
        return String.format("%-" + width + "s", v);
    }

    private static String clamp(final String text, final int maxLength) {
        if (text == null) return "-";
        final String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String applyTruncation(final String text, final OutputConfig config) {
        if (text == null || text.equals("-")) return text;
        if (config.outputMode() == OutputConfig.OutputMode.FULL) {
            return text;
        }
        // Truncated mode
        final int maxLength = config.truncateLength();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String formatUtc(final Instant instant) {
        return instant == null ? "-" : UTC_FORMATTER.format(instant);
    }

    private String formatLocal(final Instant instant) {
        return instant == null ? "-" : LOCAL_FORMATTER.format(instant.atZone(localZoneId));
    }

    private static double clampPercent(final double p) {
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Formats a NOTAM in traditional format: !LOC NUM LOC TEXT STARTTIME-ENDTIME
     */
    private String formatTraditionalNotam(final NotamView n) {
        final String location = n.location() != null ? n.location() : "UNK";
        final String number = n.notamNumber() != null ? n.notamNumber() : "00/000";
        final String text = n.conditionText() != null ? n.conditionText() : "";

        // Format times as YYMMDDHHMM for traditional NOTAM format
        final DateTimeFormatter traditionalTimeFormat =
                DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneOffset.UTC);

        final String startTime = n.startTimeUtc() != null ?
                traditionalTimeFormat.format(n.startTimeUtc()) : "0000000000";
        final String endTime = n.endTimeUtc() != null ?
                traditionalTimeFormat.format(n.endTimeUtc()) : "PERM";

        return String.format("!%s %s %s %s %s-%s",
                location, number, location, text, startTime, endTime);
    }

    /**
     * Wraps text to multiple lines for the condition column.
     * Tries to break at spaces, but will break long words if necessary.
     *
     * @param text the text to wrap
     * @return the wrapped text with newlines
     */
    private static String wrapText(final String text) {
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
                if (!currentLine.isEmpty()) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(currentLine);
                    currentLine = new StringBuilder();
                }

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

    /**
     * Builds the time column string based on the current time mode.
     */
    private String buildTimeColumns(final String startUtc, final String endUtc,
                                    final String startLocal, final String endLocal) {
        return (timeMode == TimeMode.LOCAL_ONLY)
                ? startLocal + "  " + endLocal
                : startUtc + "  " + endUtc;
    }

    /**
     * Adds local time row to the line if in BOTH mode.
     */
    private String addLocalTimeRow(String line, final String startLocal, final String endLocal) {
        if (timeMode == TimeMode.BOTH) {
            final String localLine = " ".repeat(LOCATION_COL_WIDTH + NUMBER_COL_WIDTH + CLASSIFICATION_COL_WIDTH + 6)
                    + ansi.dim(startLocal + "  " + endLocal);
            line += "\n" + localLine;
        }
        return line;
    }
}