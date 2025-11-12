package ou.capstone.notams;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ou.capstone.notams.api.NotamFetcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live FAA deduplication test for CCS-32 
 *
 * Skips automatically if FAA_CLIENT_ID / FAA_CLIENT_SECRET are not set.
 * 1) Route test: KJFK -> KLAX (multiple waypoint pages)
 * 2) Single-location test: around KJFK within 50 NM
 */
public final class LiveNotamDeduplicationTest {

    private static boolean hasEnv(final String key) {
        final String v = System.getenv(key);
        return v != null && !v.isBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable( named="RUN_LIVE_TESTS", matches = "true" )
    void fetchParseAndDedup_onRoute_KJFK_to_KLAX() throws Exception {
        // Skip if credentials missing (keeps CI green)
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_ID"), "FAA_CLIENT_ID not set; skipping live test");
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_SECRET"), "FAA_CLIENT_SECRET not set; skipping live test");

        final NotamFetcher fetcher = new NotamFetcher();

        // Fetch NOTAMs along the route (already parsed by NotamFetcher)
        final List<Notam> allNotams = fetcher.fetchForRoute("KJFK", "KLAX");

        // Deduplicate
        final List<Notam> unique = NotamDeduplication.dedup(allNotams);

        assertFalse(allNotams.isEmpty(), "Expected some live NOTAMs along the route");
        assertTrue(unique.size() <= allNotams.size(), "Deduped size must not exceed parsed size");

        System.out.println("\n[Live FAA Route Test]");
        System.out.println("Leg: KJFK -> KLAX");
        System.out.println("Parsed count: " + allNotams.size());
        System.out.println("Unique count: " + unique.size());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_TESTS", matches = "true")
    void fetchParseAndDedup_singleLocation_KJFK_radius50nm() throws Exception {
        // Skip if credentials missing
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_ID"), "FAA_CLIENT_ID not set; skipping live test");
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_SECRET"), "FAA_CLIENT_SECRET not set; skipping live test");

        final double lat = 40.6413;
        final double lon = -73.7781;
        final int radiusNm = 50;

        final NotamFetcher fetcher = new NotamFetcher();

        // Fetch NOTAMs for location (already parsed by NotamFetcher)
        final List<Notam> parsed = fetcher.fetchForLocation(lat, lon, radiusNm);

        // Deduplicate
        final List<Notam> unique = NotamDeduplication.dedup(parsed);

        assertFalse(parsed.isEmpty(), "Expected some live NOTAMs near KJFK");
        assertTrue(unique.size() <= parsed.size(), "Deduped size must not exceed parsed size");

        System.out.println("\n[Live FAA Single-Location Test]");
        System.out.println("Location: KJFK ~" + radiusNm + " NM");
        System.out.println("Parsed count: " + parsed.size());
        System.out.println("Unique count: " + unique.size());
    }
}