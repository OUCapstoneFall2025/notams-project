package ou.capstone.notams.print;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Minimal UI-friendly NOTAM DTO. */
public record NotamView(
        String notamNumber,
        String location,
        String classification,
        Instant startTimeUtc,
        Instant endTimeUtc,
        String conditionText,
        Double score // nullable if not applicable
) {
    private static final DateTimeFormatter TRADITIONAL_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneOffset.UTC);

    /**
     * Returns the traditional NOTAM format: !LOC NUM LOC TEXT STARTTIME-ENDTIME
     */
    public String formatTraditionalNotam() {
        final String loc = location != null ? location : "UNK";
        final String num = notamNumber != null ? notamNumber : "00/000";
        final String txt = conditionText != null ? conditionText : "";

        final String start = startTimeUtc != null ?
                TRADITIONAL_TIME_FORMAT.format(startTimeUtc) : "0000000000";
        final String end = endTimeUtc != null ?
                TRADITIONAL_TIME_FORMAT.format(endTimeUtc) : "PERM";

        return String.format("!%s %s %s %s %s-%s", loc, num, loc, txt, start, end);
    }
}
