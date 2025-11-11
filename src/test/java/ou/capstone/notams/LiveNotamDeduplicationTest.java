package ou.capstone.notams;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ou.capstone.notams.api.NotamFetcher;
import ou.capstone.notams.api.NotamParser;

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

    private static boolean hasEnv(String key) {
        String v = System.getenv(key);
        return v != null && !v.isBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable( named="RUN_LIVE_TESTS", matches = "true" )
    void fetchParseAndDedup_onRoute_KJFK_to_KLAX() throws Exception {
        // Skip if credentials missing (keeps CI green)
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_ID"), "FAA_CLIENT_ID not set; skipping live test");
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_SECRET"), "FAA_CLIENT_SECRET not set; skipping live test");

        NotamFetcher fetcher = new NotamFetcher();
        NotamParser parser   = new NotamParser();

        // Fetch multiple GeoJSON pages along the route
        List<String> pages = fetcher.fetchForRoute("KJFK", "KLAX");

        List<Notam> parsedAll = new ArrayList<>();
        for (String geoJson : pages) {
            parsedAll.addAll(parser.parseGeoJson(geoJson));
        }

        List<Notam> unique = NotamDeduplication.dedup(parsedAll);


        assertFalse(parsedAll.isEmpty(), "Expected some live NOTAMs along the route");
        assertTrue(unique.size() <= parsedAll.size(), "Deduped size must not exceed parsed size");

        System.out.println("\n[Live FAA Route Test]");
        System.out.println("Leg: KJFK -> KLAX");
        System.out.println("Pages fetched: " + pages.size());
        System.out.println("Parsed count: " + parsedAll.size());
        System.out.println("Unique count: " + unique.size());
    }

    @Test
    @EnabledIfEnvironmentVariable( named="RUN_LIVE_TESTS", matches = "true" )
    void fetchParseAndDedup_singleLocation_KJFK_radius50nm() throws Exception {
        // Skip if credentials missing
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_ID"), "FAA_CLIENT_ID not set; skipping live test");
        Assumptions.assumeTrue(hasEnv("FAA_CLIENT_SECRET"), "FAA_CLIENT_SECRET not set; skipping live test");

        double lat = 40.6413;
        double lon = -73.7781;
        int radiusNm = 50;

        NotamFetcher fetcher = new NotamFetcher();
        NotamParser parser   = new NotamParser();

        String geoJson = fetcher.fetchForLocation(lat, lon, radiusNm);

        List<Notam> parsed = parser.parseGeoJson(geoJson);
        List<Notam> unique = NotamDeduplication.dedup(parsed);

        assertFalse(parsed.isEmpty(), "Expected some live NOTAMs near KJFK");
        assertTrue(unique.size() <= parsed.size(), "Deduped size must not exceed parsed size");

        System.out.println("\n[Live FAA Single-Location Test]");
        System.out.println("Location: KJFK ~" + radiusNm + " NM");
        System.out.println("Parsed count: " + parsed.size());
        System.out.println("Unique count: " + unique.size());
    }
}
