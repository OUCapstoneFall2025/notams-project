package ou.capstone.notams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class NotamScoringDemoTest {

    private static Set<String> keys() {
        return new HashSet<>(Arrays.asList("KJFK", "KLAX"));
    }

    @Test
    void runwayClosure_scoresAbove_navOutage_scoresAbove_minor() {
        final Instant now = Instant.parse("2025-09-22T12:00:00Z");
        final Instant dep = now.plusSeconds(3600);
        final Instant arr = now.plusSeconds(3 * 3600);

        final NotamScoringDemo.Scorer scorer = new NotamScoringDemo.Scorer();

        final NotamScoringDemo.ScoredNotam closure = new NotamScoringDemo.ScoredNotam(
            "N1","KJFK","RUNWAY CLOSED RWY 13L/31R", 2.0,
            now.plusSeconds(1800), now.plusSeconds(7200), now.minusSeconds(3600));

        final NotamScoringDemo.ScoredNotam vorOut = new NotamScoringDemo.ScoredNotam(
            "N2","KJFK","VOR OUT OF SERVICE", 8.0,
            now.plusSeconds(7200), now.plusSeconds(5*3600), now.minusSeconds(30*3600));

        final NotamScoringDemo.ScoredNotam apron = new NotamScoringDemo.ScoredNotam(
            "N3","KDEN","ADVISORY APRON WORK", 22.0,
            now.plusSeconds(20000), now.plusSeconds(30000), now.minusSeconds(50*3600));

        final int sClosure = scorer.score(closure, dep, arr, keys());
        final int sVor     = scorer.score(vorOut, dep, arr, keys());
        final int sApron   = scorer.score(apron, dep, arr, keys());

        assertTrue(sClosure >= sVor && sVor >= sApron, "closure >= nav outage >= minor advisory");
        assertTrue(sClosure <= 100 && sClosure >= 0);
    }

    @Test
    void keyAirport_adds_10_points() {
        final Instant now = Instant.parse("2025-09-22T12:00:00Z");
        final Instant dep = now.plusSeconds(3600);
        final Instant arr = now.plusSeconds(3 * 3600);
        final NotamScoringDemo.Scorer scorer = new NotamScoringDemo.Scorer();

        final NotamScoringDemo.ScoredNotam atKey = new NotamScoringDemo.ScoredNotam(
            "K","KJFK","ADVISORY", 2.0, dep, arr, now.minusSeconds(1000));
        final NotamScoringDemo.ScoredNotam notKey = new NotamScoringDemo.ScoredNotam(
            "N","KSEA","ADVISORY", 2.0, dep, arr, now.minusSeconds(1000));

        final int sKey = scorer.score(atKey, dep, arr, keys());
        final int sNon = scorer.score(notKey, dep, arr, keys());
        assertEquals(10, sKey - sNon);
    }

    @Test
    void outside_window_timeOverlap_is_zero() {
        final Instant now = Instant.parse("2025-09-22T12:00:00Z");
        final Instant dep = now.plusSeconds(3600);
        final Instant arr = now.plusSeconds(3 * 3600);
        final NotamScoringDemo.Scorer scorer = new NotamScoringDemo.Scorer();

       
        final NotamScoringDemo.ScoredNotam before = new NotamScoringDemo.ScoredNotam(
            "B", "KSEA", "ADVISORY", 25.0,
            now.minusSeconds(7200),   // NOTAM ends 2h before dep
            now.minusSeconds(3600),   // NOTAM ends 1h before dep
            now.minusSeconds(50 * 3600) // issued 50h ago
        );

        final int s = scorer.score(before, dep, arr, keys());
        // Expected: impact=10, proximity=0, time=0, freshness=0, key=0 => total=10
        assertTrue(s <= 10, "outside-window distant stale advisory should be very low");
    }
}
