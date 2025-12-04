package ou.capstone.notams.prioritize;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ou.capstone.notams.Notam;
import ou.capstone.notams.prioritize.NotamPrioritizer.Mode;

/**
 * Simple, explainable prioritizer.
 *
 * Now delegates scoring to a pluggable NotamScorer hierarchy:
 *  - PatternMatchingScorer (type, keywords, NAV aids, obstacles, fuel)
 *  - ProximityScorer (radius / departure / destination / region penalty)
 *  - RecencyScorer (how recent the NOTAM is)
 *
 * This keeps SimplePrioritizer focused on "sort + tie-breaking", and moves
 * scoring details into separate, testable classes.
 */
public class SimplePrioritizer implements NotamPrioritizer {

    private final Clock clock;
    private final String departureAirport;
    private final String destinationAirport;
    private final Mode mode;

    private final NotamScorer scorer;

    /** Default constructor: IFR, system clock, no specific route. */
    public SimplePrioritizer() {
        this(Clock.systemUTC());
    }

    /** Constructor used by tests to inject a fixed clock. */
    public SimplePrioritizer(final Clock clock) {
        this(clock, null, null, Mode.IFR);
    }

    /** Convenience: use system clock with explicit route + mode. */
    public SimplePrioritizer(final String departureAirport,
                             final String destinationAirport,
                             final Mode mode) {
        this(Clock.systemUTC(), departureAirport, destinationAirport, mode);
    }

    /**
     * Full constructor, for the specialized prioritizers(could be used in the future).
     */
    public SimplePrioritizer(final Clock clock,
                             final String departureAirport,
                             final String destinationAirport,
                             final Mode mode) {
        this.clock = clock;
        this.departureAirport = departureAirport;
        this.destinationAirport = destinationAirport;
        this.mode = (mode != null) ? mode : Mode.IFR;

        // Wiring of the scorer.
        this.scorer = new CompositeNotamScorer(List.of(
                new PatternMatchingScorer(this.mode),
                new ProximityScorer(this.departureAirport, this.destinationAirport),
                new RecencyScorer(this.clock)
        ));
    }

    @Override
    public List<Notam> prioritize(final List<Notam> notams) {
        final List<Notam> copy = new ArrayList<>(notams);
        // Tie-breakers provide stable ordering when scores are equal
        copy.sort(Comparator.<Notam>comparingDouble(this::score).reversed()
                .thenComparing(Notam::getIssued, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Notam::getId, Comparator.nullsLast(String::compareTo)));
        return copy;
    }

    /**
     * Made public to satisfy NotamPrioritizer interface contract.
     * For display purposes, prefer scoreForDisplay() which rounds the result.
     */
    @Override
    public double score(final Notam n) {
        if (n == null) {
            return 0.0;
        }
        return scorer.score(n);
    }

    /**
     * Public helper used by App.java to render a rounded score.
     * This keeps display formatting out of the core scoring logic.
     */
    public double scoreForDisplay(final Notam n) {
        // Round to 2 decimal places, like "55.05"
        final double raw = score(n);
        return Math.round(raw * 100.0) / 100.0;
    }
}