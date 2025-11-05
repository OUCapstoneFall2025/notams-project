package ou.capstone.notams.print;

import java.time.ZoneId;

/** Printer with ANSI colors enabled. */

public class NotamColorPrinter extends NotamPrinter {
    public NotamColorPrinter(final ZoneId zoneId, final NotamPrinter.TimeMode timeMode) {
        super(zoneId, timeMode, true, 0.10, 0.20);
    }

    public NotamColorPrinter(final ZoneId zoneId) {
        super(zoneId, NotamPrinter.TimeMode.BOTH, true, 0.10, 0.20);
    }
}