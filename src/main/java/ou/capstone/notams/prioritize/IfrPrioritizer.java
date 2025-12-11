package ou.capstone.notams.prioritize;

import java.time.Clock;

/**
 * IFR-focused prioritizer
 * around SimplePrioritizer with Mode.IFR.
 */
public final class IfrPrioritizer extends SimplePrioritizer {

    public IfrPrioritizer() {
        super(Clock.systemUTC());
    }

    public IfrPrioritizer(final Clock clock,
                          final String departureAirport,
                          final String destinationAirport) {
        super(clock, departureAirport, destinationAirport, Mode.IFR);
    }
}