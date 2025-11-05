package ou.capstone.notams.print;

import java.time.Instant;

/** Minimal UI-friendly NOTAM DTO. */
public record NotamView(
        String notamNumber,
        String location,
        String classification,
        Instant startTimeUtc,
        Instant endTimeUtc,
        String conditionText,
        Double score // nullable if not applicable
) {}
