package ou.capstone.notams.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for NOTAM API page size optimization.
 * Tests various page sizes to determine optimal configuration.
 * Only runs when FAA_CLIENT_ID and FAA_CLIENT_SECRET are set.
 * Tagged as "performance" to allow exclusion from regular test runs.
 */
@Tag("performance")
@EnabledIfEnvironmentVariable( named="RUN_LIVE_TESTS", matches = "true" )
class NotamApiPageSizeTest {

    private static final Logger logger = LoggerFactory.getLogger(NotamApiPageSizeTest.class);

    private static final int[] PAGE_SIZES_TO_TEST = {10, 25, 50, 100, 200, 500, 750, 1000};
    private static final int REQUEST_DELAY_MS = 500;

    private static final double TEST_LATITUDE = 32.8998;
    private static final double TEST_LONGITUDE = -97.0403;
    private static final int TEST_RADIUS_NM = 50;

    private static final String NOTAM_NUMBER_FIELD = "\"notamNumber\"";

    @BeforeAll
    static void validateSetup() {
        FaaNotamApiWrapper.validateCredentials();
        logger.info("Starting page size performance test");
        logger.info("Test location: lat={}, lon={}, radius={} NM",
                TEST_LATITUDE, TEST_LONGITUDE, TEST_RADIUS_NM);
    }

    @Test
    @DisplayName("Compare performance across different page sizes")
    void testPageSizePerformance() throws Exception {
        logger.info("Page Size | Time (ms) | NOTAMs Found | Status");
        logger.info("----------|-----------|--------------|------------------");

        final List<TestResult> results = new ArrayList<>();

        for (final int pageSize : PAGE_SIZES_TO_TEST) {
            final TestResult result = testSinglePageSize(pageSize);
            results.add(result);
            logResult(result);

            Thread.sleep(REQUEST_DELAY_MS);
        }

        logSummary(results);

        assertFalse(results.isEmpty(), "Should have collected test results");
        assertTrue(results.stream().anyMatch(r -> r.success),
                "At least one page size should succeed");
    }

    private TestResult testSinglePageSize(final int pageSize) {
        logger.debug("Testing page size: {}", pageSize);

        try {
            final long startTime = System.currentTimeMillis();

            final FaaNotamApiWrapper.QueryParamsBuilder queryParams =
                    new FaaNotamApiWrapper.QueryParamsBuilder(TEST_LATITUDE, TEST_LONGITUDE, TEST_RADIUS_NM)
                            .pageSize(pageSize);

            final String response = FaaNotamApiWrapper.fetchRawJson(queryParams);

            final long elapsedTime = System.currentTimeMillis() - startTime;
            final int notamCount = countNotams(response);
            final boolean morePages = notamCount >= pageSize;

            logger.debug("Page size {} completed: {} ms, {} NOTAMs",
                    pageSize, elapsedTime, notamCount);

            return new TestResult(pageSize, elapsedTime, notamCount, morePages, true, null);

        } catch (final Exception e) {
            logger.warn("Error testing page size {}: {}", pageSize, e.getMessage());
            return new TestResult(pageSize, 0, 0, false, false, e.getMessage());
        }
    }

    private int countNotams(final String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return 0;
        }

        // Try different field names that might appear in the GeoJSON response
        // The features array in GeoJSON contains the NOTAMs
        int count = 0;

        // Count occurrences of "type":"Feature" which is the GeoJSON standard
        String featureMarker = "\"type\":\"Feature\"";
        int index = 0;
        while ((index = responseBody.indexOf(featureMarker, index)) != -1) {
            count++;
            index += featureMarker.length();
        }

        // If no features found, try counting notamNumber as fallback
        if (count == 0) {
            index = 0;
            while ((index = responseBody.indexOf(NOTAM_NUMBER_FIELD, index)) != -1) {
                count++;
                index += NOTAM_NUMBER_FIELD.length();
            }
        }

        return count;
    }

    private void logResult(final TestResult result) {
        if (result.success) {
            final String status = result.morePages
                    ? "May have more pages"
                    : "All NOTAMs retrieved";

            logger.info(String.format(Locale.ROOT, "%-10d| %-10d| %-13d| %s",
                    result.pageSize, result.elapsedTimeMs, result.notamCount, status));
        } else {
            logger.info(String.format(Locale.ROOT, "%-10d| %-10s| %-13s| ERROR: %s",
                    result.pageSize, "N/A", "N/A", result.errorMessage));
        }
    }

    private void logSummary(final List<TestResult> results) {
        logger.info("");
        logger.info("SUMMARY:");
        logger.info("--------");

        final List<TestResult> successfulResults = results.stream()
                .filter(r -> r.success)
                .toList();

        if (successfulResults.isEmpty()) {
            logger.info("No successful results to analyze");
            return;
        }

        final TestResult fastest = successfulResults.stream()
                .min(Comparator.comparingLong(r -> r.elapsedTimeMs))
                .orElseThrow();

        logger.info("Fastest: Page size {} ({} ms)",
                fastest.pageSize, fastest.elapsedTimeMs);

        // Filter out results with zero NOTAMs for efficiency calculation
        final List<TestResult> resultsWithNotams = successfulResults.stream()
                .filter(r -> r.notamCount > 0)
                .toList();

        if (!resultsWithNotams.isEmpty()) {
            final TestResult mostEfficient = resultsWithNotams.stream()
                    .min((r1, r2) -> {
                        final double efficiency1 = (double) r1.elapsedTimeMs / r1.notamCount;
                        final double efficiency2 = (double) r2.elapsedTimeMs / r2.notamCount;
                        return Double.compare(efficiency1, efficiency2);
                    })
                    .orElseThrow();

            final double msPerNotam = (double) mostEfficient.elapsedTimeMs / mostEfficient.notamCount;
            logger.info("Most efficient: Page size {} ({} ms per NOTAM)",
                    mostEfficient.pageSize, String.format(Locale.ROOT, "%.2f", msPerNotam));
        } else {
            logger.info("No NOTAMs found in any response - check location or API response format");
        }

        logger.info("");
        logger.info("NOTE: Balance individual request speed vs total requests needed");
        logger.info("Current NotamFetcher default: pageSize=100");
    }

    private static class TestResult {
        final int pageSize;
        final long elapsedTimeMs;
        final int notamCount;
        final boolean morePages;
        final boolean success;
        final String errorMessage;

        TestResult(final int pageSize, final long elapsedTimeMs, final int notamCount,
                   final boolean morePages, final boolean success, final String errorMessage) {
            this.pageSize = pageSize;
            this.elapsedTimeMs = elapsedTimeMs;
            this.notamCount = notamCount;
            this.morePages = morePages;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}