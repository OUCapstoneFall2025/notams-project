package ou.capstone.notams.prioritize;

import java.time.Clock;
import java.util.List;

import ou.capstone.notams.Notam;

/**
 * Start of a complex prioritizer, could be expanded upon if we decide
 * to in the future. For now it wraps the
 * SimplePrioritizer so we can compare behaviors if needed.
 */
public final class ComplexPrioritizer implements NotamPrioritizer {

    private final SimplePrioritizer delegate;

    public ComplexPrioritizer(final Clock clock,
                              final String departureAirport,
                              final String destinationAirport,
                              final Mode mode) {
        this.delegate = new SimplePrioritizer(clock, departureAirport, destinationAirport, mode);
    }

    @Override
    public List<Notam> prioritize(final List<Notam> notams) {
        return delegate.prioritize(notams);
    }

    @Override
    public double score(final Notam n) {
        return delegate.score(n);
    }
}