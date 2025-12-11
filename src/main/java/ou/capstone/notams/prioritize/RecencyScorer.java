package ou.capstone.notams.prioritize;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import ou.capstone.notams.Notam;

/**
 * Scores NOTAMs based on how recent they are.
 */
public final class RecencyScorer implements NotamScorer {

    // ---- Recency knobs ----
    private static final double W_RECENCY_MAX = 20.0; // full credit if <= 24h old
    private static final int RECENCY_HALF_LIFE_HOURS = 72; // exponential decay

    private final Clock clock;

    public RecencyScorer(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public double score(final Notam notam) {
        final OffsetDateTime issued = notam.getIssued();
        if (issued == null) {
            return 0.0;
        }
        final long hours = Math.max(
                0L,
                Duration.between(issued, OffsetDateTime.now(clock)).toHours()
        );

        if (hours <= 24L) {
            return W_RECENCY_MAX;
        }

        final double decayHours = (double) (hours - 24L);
        final double factor = Math.pow(0.5,
                decayHours / (double) RECENCY_HALF_LIFE_HOURS);

        return W_RECENCY_MAX * factor;
    }
}