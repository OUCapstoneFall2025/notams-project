package ou.capstone.notams.prioritize;

import java.util.Locale;

import ou.capstone.notams.Notam;

/**
 * Extra boost for serious fuel unavailability conditions
 * (e.g., ALL fuel out, or multiple fuel types unavailable).
 */
public final class SeriousFuelUnScorer extends FuelUnavailableScorer {

    private static final double W_SERIOUS_FUEL = 65.0; // stacked on top of base

    @Override
    public double score(final Notam notam) {
        final String text = notam.getText();
        if (text == null) {
            return 0.0;
        }
        final String s = text.toUpperCase(Locale.ROOT);

        // Reuse the "normal" fuel scoring
        double base = super.score(notam);

        // Serious cases – all fuel / all self-serve out, etc.
        if (s.contains("ALL FUEL")
                || s.contains("NO FUEL")
                || s.contains("SELF SERVE 100LL FUEL NOT AVBL")
                || s.contains("SELF SERVE JET A FUEL NOT AVBL")) {
            base += W_SERIOUS_FUEL;
        }

        return base;
    }
}