package ou.capstone.notams.print;

import java.time.ZoneId;

/** Printer with ANSI colors enabled. */

public class NotamColorPrinter extends NotamPrinter {
    public NotamColorPrinter(final ZoneId zoneId, final NotamPrinter.TimeMode timeMode) {
        super(zoneId, timeMode, true);
    }

    public NotamColorPrinter(final ZoneId zoneId) {
        super(zoneId, NotamPrinter.TimeMode.BOTH, true);
    }
}