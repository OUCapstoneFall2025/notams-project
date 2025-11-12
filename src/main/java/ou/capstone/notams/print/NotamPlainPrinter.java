package ou.capstone.notams.print;

import java.time.ZoneId;

/** Printer with ANSI colors disabled (plain text). */
public final class NotamPlainPrinter extends NotamPrinter {
    public NotamPlainPrinter(final ZoneId zoneId, final TimeMode timeMode) {
        super(zoneId, timeMode, false, 0.10, 0.20);
    }

    public NotamPlainPrinter(final ZoneId zoneId) {
        super(zoneId, TimeMode.BOTH, false, 0.10, 0.20);
    }
}