package ou.capstone.notams.print;

import java.time.ZoneId;

/** Printer with ANSI colors disabled (plain text). */
public final class NotamPlainPrinter extends NotamPrinter {
    public NotamPlainPrinter(final ZoneId zoneId, final TimeMode timeMode) {
        super(zoneId, timeMode);
    }

    public NotamPlainPrinter(final ZoneId zoneId) {
        super(zoneId, TimeMode.BOTH);
    }
    
    @Override
    protected String decorateConditionLine(
            final NotamView n,
            final String line,
            final Double scoreValue
    ) {
        // No coloring 
        return line;
    }
}
