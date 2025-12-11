package ou.capstone.notams.prioritize;

import java.util.List;

import ou.capstone.notams.Notam;

/**
 * Combines several NotamScorer rules by summing their contributions.
 */
public final class CompositeNotamScorer implements NotamScorer {

    private final List<NotamScorer> scorers;

    public CompositeNotamScorer(final List<NotamScorer> scorers) {
        this.scorers = List.copyOf(scorers);
    }

    @Override
    public double score(final Notam notam) {
        double total = 0.0;
        for (NotamScorer s : scorers) {
            total += s.score(notam);
        }
        return total;
    }
}