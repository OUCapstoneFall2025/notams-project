package ou.capstone.notams.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ou.capstone.notams.Notam;

/**
 * Live integration test for NotamFetcher using the FAA API.
 * 
 * This test verifies:
 *  1. Parallel fetching works end-to-end.
 *  2. The entire route fetch completes under 60s.
 *  3. The FAA credentials are detected correctly.
 */
class NotamFetcherParallelLiveIT {

    @Test
    @Timeout(60) // must finish within one minute
    void live_parallelFetch_kokcToKdfw_completesUnderOneMinute() throws Exception {

        // --- skip test if FAA credentials not present ---
        String clientId = System.getenv("FAA_CLIENT_ID");
        String clientSecret = System.getenv("FAA_CLIENT_SECRET");

        Assumptions.assumeTrue(clientId != null && !clientId.isBlank(),
                "FAA_CLIENT_ID not set — skipping live test.");
        Assumptions.assumeTrue(clientSecret != null && !clientSecret.isBlank(),
                "FAA_CLIENT_SECRET not set — skipping live test.");

        // Real test
        NotamFetcher fetcher = new NotamFetcher();

        long start = System.currentTimeMillis();
        List<Notam> notams = fetcher.fetchForRoute("KOKC", "KDFW");
        long elapsed = System.currentTimeMillis() - start;

        // Basic validations
        assertNotNull(notams, "NOTAM list should never be null");

        // expect at least 1 NOTAM
        assertFalse(notams.isEmpty(), "Expected at least one NOTAM from FAA API");

        // Speed check
        assertTrue(
                elapsed < 60_000,
                "Parallel NOTAM fetch took too long: " + elapsed + " ms (expected < 60,000 ms)"
        );

        System.out.println("LIVE PARALLEL FETCH OK — time: " + elapsed + " ms, NOTAMs: " + notams.size());
    }
}
