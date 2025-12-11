package ou.capstone.notams.prioritize;

import java.util.Locale;

import ou.capstone.notams.Notam;

/**
 * Scores NOTAMs mentioning fuel unavailability.
 */
public class FuelUnavailableScorer implements NotamScorer {

    // Base weight when any fuel is unavailable
    protected static final double W_FUEL_UNAVAILABLE = 35.0;

    @Override
    public double score(final Notam notam) {
        final String text = notam.getText();
        if (text == null) {
            return 0.0;
        }
        final String s = text.toUpperCase(Locale.ROOT);

        if (s.contains("FUEL NOT AVBL")
                || s.contains("FUEL NOT AVAIL")
                || s.contains("FUEL NOT AVAILABLE")
                || s.contains("FUEL UNAVAIL")) {
            return W_FUEL_UNAVAILABLE;
        }

        return 0.0;
    }
}