package ou.capstone.notams.prioritize;

import java.time.Clock;

/**
 * VFR-focused prioritizer wrapper for Mode.VFR.
 */
public final class VfrPrioritizer extends SimplePrioritizer {

    public VfrPrioritizer() {
        super(Clock.systemUTC(), null, null, Mode.VFR);
    }

    public VfrPrioritizer(final Clock clock,
                          final String departureAirport,
                          final String destinationAirport) {
        super(clock, departureAirport, destinationAirport, Mode.VFR);
    }
}