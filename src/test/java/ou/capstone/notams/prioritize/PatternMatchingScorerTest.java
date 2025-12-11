package ou.capstone.notams.prioritize;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ou.capstone.notams.Notam;

class PatternMatchingScorerTest {

    private static Notam makeNotam(String id, String type, String text) {
        return new Notam.Builder()
                .id(id)
                .number("1/01")
                .type(type)
                .location("KTEST")
                .issued(OffsetDateTime.parse("2025-10-04T20:00:00Z"))
                .text(text)
                .build();
    }

    @Test
    void uasAirspaceNotamsGetMoreScoreThanPlainAirspace() {
        var scorer = new PatternMatchingScorer();

        Notam plain = makeNotam("A", "AIRSPACE",
                "AIRSPACE WI AN AREA DEFINED AS 3NM RADIUS OF 323410N0961827W");
        Notam uas = makeNotam("B", "AIRSPACE",
                "AIRSPACE UAS WI AN AREA DEFINED AS 3NM RADIUS OF 323410N0961827W");

        double baseScore = scorer.score(plain);
        double uasScore = scorer.score(uas);

        assertTrue(uasScore > baseScore,
                "UAS airspace NOTAM should score higher than plain AIRSPACE");
    }

    @Test
    void gliderAirspaceNotamsGetMoreScoreThanPlainAirspace() {
        var scorer = new PatternMatchingScorer();

        Notam plain = makeNotam("C", "AIRSPACE",
                "AIRSPACE WI AN AREA DEFINED AS 10NM RADIUS OF TOY049016.2 SFC-8000FT");
        Notam glider = makeNotam("D", "AIRSPACE",
                "AIRSPACE GLD WI AN AREA DEFINED AS 10NM RADIUS OF TOY049016.2 SFC-8000FT");

        double baseScore = scorer.score(plain);
        double gliderScore = scorer.score(glider);

        assertTrue(gliderScore > baseScore,
                "GLD airspace NOTAM should score higher than plain AIRSPACE");
    }

    @Test
    void highSpeedAirspaceNotamsGetMoreScoreThanPlainAirspace() {
        var scorer = new PatternMatchingScorer();

        Notam plain = makeNotam("E", "AIRSPACE",
                "AIRSPACE WI AN AREA DEFINED AS 100NM RADIUS OF ICT SFC-FL180");
        Notam highSpeed = makeNotam("F", "AIRSPACE",
                "AIRSPACE HIGH SPEED ACFT WI AN AREA DEFINED AS 100NM RADIUS OF ICT SFC-FL180");

        double baseScore = scorer.score(plain);
        double highSpeedScore = scorer.score(highSpeed);

        assertTrue(highSpeedScore > baseScore,
                "HIGH SPEED airspace NOTAM should score higher than plain AIRSPACE");
    }
}