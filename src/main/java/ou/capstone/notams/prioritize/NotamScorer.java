package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;

/**
 * Assigns a numeric score to a NOTAM.
 * Higher score = higher priority.
 */
@FunctionalInterface
public interface NotamScorer {

    /**
     * Returns the score for the given NOTAM.
     *
     * @param notam the NOTAM to score (may be null)
     * @return a finite double score
     */
    double score(Notam notam);
}