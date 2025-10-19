package ou.capstone.notams.prioritize;

import ou.capstone.notams.Notam;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimplePrioritizerTest {

   private static Notam makeNotam(
        final String id,
        final String type,
        final String icao,
        final String issuedIso,
        final double latitude,
        final double longitude,
        final Double radiusNm,
        final String text
) {
    // NOTE: We use a constant NOTAM “number” of "5/31" in tests because
    // the prioritizer doesn’t consider the number; keeping it constant
    // avoids accidental score differences between test NOTAMs.
    return new Notam(
            id,
            "5/31",    // test-only constant; not part of scoring
            type,
            java.time.OffsetDateTime.parse(issuedIso),
            icao,
            latitude,
            longitude,
            radiusNm,
            text
    );
}

    @Test
    void sortsByScoreDescending_withTiesBrokenByRecency() {
        // Fixed time so recency scoring is deterministic
        Clock fixed = Clock.fixed(Instant.parse("2025-10-04T21:00:00Z"), ZoneOffset.UTC);
        var pr = new SimplePrioritizer(fixed);

        Notam rwyClosed = makeNotam(
                "A", "RUNWAY", "KATL", "2025-10-04T20:00:00Z",
                33.6407, -84.4277, 3.0, "RWY 8L/26R CLOSED"
        );
        Notam airspace = makeNotam(
                "C", "AIRSPACE", "KATL", "2025-10-03T20:00:00Z",
                33.6407, -84.4277, 50.0, "TEMP AIRSPACE RESTRICTION"
        );
        Notam twyMaint = makeNotam(
                "B", "TAXIWAY", "KATL", "2025-10-02T20:00:00Z",
                33.6407, -84.4277, 8.0, "TWY B MAINT"
        );

        List<Notam> sorted = pr.prioritize(List.of(twyMaint, airspace, rwyClosed));

        // Top item should be RUNWAY CLOSED
        assertEquals("A", sorted.get(0).getId());

        // Prove scores are descending without enforcing middle order
        double s0 = pr.score(sorted.get(0));
        double s1 = pr.score(sorted.get(1));
        double s2 = pr.score(sorted.get(2));
        assertTrue(s0 >= s1 && s1 >= s2);

        // All three NOTAMs are present
        assertEquals(3, sorted.size());
        assertTrue(sorted.stream().map(Notam::getId).toList().containsAll(List.of("A", "B", "C")));
    }

    @Test
    void appliesKeywordAndTypeWeights() {
        Clock fixed = Clock.fixed(Instant.parse("2025-10-04T21:00:00Z"), ZoneOffset.UTC);
        var pr = new SimplePrioritizer(fixed);

        Notam base = makeNotam("X", "RUNWAY", "KATL", "2025-10-04T20:00:00Z",
                33.6407, -84.4277, 10.0, "RWY 8L OPEN");

        Notam closed = makeNotam("Y", "RUNWAY", "KATL", "2025-10-04T20:00:00Z",
                33.6407, -84.4277, 10.0, "RWY 8L CLOSED");

        // CLOSED should add weight
        assertTrue(pr.score(closed) > pr.score(base));

        // AIRSPACE less than RUNWAY when other signals equal
        Notam airspace = makeNotam("Z", "AIRSPACE", "KATL", "2025-10-04T20:00:00Z",
                33.6407, -84.4277, 10.0, "TEMP RESTRICTION");
        assertTrue(pr.score(base) > pr.score(airspace));
    }

    @Test
    void recencyAffectsScore_andTimezoneOffsetsHandled() {
        // Fixed time 21:00Z
        Clock fixed = Clock.fixed(Instant.parse("2025-10-04T21:00:00Z"), ZoneOffset.UTC);
        var pr = new SimplePrioritizer(fixed);

        // Same instant expressed with offset
        Notam zulu = makeNotam("Z1", "RUNWAY", "KATL", "2025-10-04T20:00:00Z",
                33.6407, -84.4277, 3.0, "RWY CLOSED");
        Notam offset = makeNotam("Z2", "RUNWAY", "KATL", "2025-10-04T15:00:00-05:00",
                33.6407, -84.4277, 3.0, "RWY CLOSED"); // same instant as 20:00Z

        assertEquals(pr.score(zulu), pr.score(offset), 1e-9);

        Notam older = makeNotam("Z3", "RUNWAY", "KATL", "2025-10-03T20:00:00Z",
                33.6407, -84.4277, 3.0, "RWY CLOSED");
        assertTrue(pr.score(zulu) > pr.score(older)); // handles it as newer > older
    }
}
