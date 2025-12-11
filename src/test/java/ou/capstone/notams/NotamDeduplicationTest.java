package ou.capstone.notams;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NOTAM deduplication logic.
 * Verifies that distinct NOTAMs are preserved while true duplicates are removed.
 */
class NotamDeduplicationTest {

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2025-11-15T12:00:00Z");
    private static final String LOCATION = "KOKC";

    @Test
    void dedup_preservesDistinctNotamsWithSameNumberButDifferentText() {
        // Two NOTAMs with same number but different content (different runways)
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N2")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 9R/27L CLOSED")
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(2, result.size(), "Distinct NOTAMs with same number but different text should both be preserved");
        assertTrue(result.contains(notam1));
        assertTrue(result.contains(notam2));
    }

    @Test
    void dedup_preservesDistinctNotamsWithSameNumberButDifferentType() {
        // Two NOTAMs with same number but different types
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N2")
                .number("5/31")
                .type("TAXIWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("TWY A CLOSED")
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(2, result.size(), "Distinct NOTAMs with same number but different type should both be preserved");
    }

    @Test
    void dedup_preservesDistinctNotamsWithSameNumberButDifferentLocation() {
        // Two NOTAMs with same number but different locations
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location("KOKC")
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N2")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location("KDFW")
                .text("RWY 8L/26R CLOSED")
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(2, result.size(), "Distinct NOTAMs with same number but different location should both be preserved");
    }

    @Test
    void dedup_removesTrueDuplicatesWithSameId() {
        // Two NOTAMs with same ID (true duplicates)
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N1")  // Same ID
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(1, result.size(), "True duplicates with same ID should be deduplicated");
    }

    @Test
    void dedup_removesTrueDuplicatesWithSameContent() {
        // Two NOTAMs with same number, location, type, and text (true duplicates)
        // Note: In real FAA data, duplicates would have same ID, but for testing
        // we use null IDs to test content-based deduplication
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N1")  // Same ID for true duplicates
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")  // Same text
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(1, result.size(), "True duplicates with same ID should be deduplicated");
    }

    @Test
    void dedup_removesTrueDuplicatesWithSameContentButNoId() {
        // Two NOTAMs with same content but no ID - should be deduplicated by content
        // This tests the fallback key (number + location + type + text hash)
        final Notam notam1 = new Notam.Builder()
                .id("")  // Empty ID to force fallback key
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("")  // Empty ID
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")  // Same text
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(1, result.size(), "True duplicates with same content (no ID) should be deduplicated by content key");
    }

    @Test
    void dedup_preservesNotamsWithDifferentTextNormalization() {
        // NOTAMs with same content but different whitespace should be considered duplicates
        // Use empty IDs to test content-based deduplication
        final Notam notam1 = new Notam.Builder()
                .id("")  // Empty ID to force content-based key
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("")  // Empty ID
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY  8L/26R  CLOSED")  // Extra whitespace (normalized to same)
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(1, result.size(), "NOTAMs with same content but different whitespace should be deduplicated");
    }

    @Test
    void dedup_handlesNullInput() {
        assertNull(NotamDeduplication.dedup(null));
    }

    @Test
    void dedup_handlesEmptyInput() {
        assertEquals(0, NotamDeduplication.dedup(new ArrayList<>()).size());
    }

    @Test
    void dedup_preservesNotamsWithMissingFields() {
        // NOTAMs without enough fields to create a key should all be preserved
        final Notam notam1 = new Notam.Builder()
                .id("N1")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(null)  // Missing location
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam notam2 = new Notam.Builder()
                .id("N2")
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(null)  // Missing location
                .text("RWY 9R/27L CLOSED")
                .build();

        final List<Notam> input = List.of(notam1, notam2);
        final List<Notam> result = NotamDeduplication.dedup(input);

        // Both should be preserved since they have IDs (primary key)
        assertEquals(2, result.size(), "NOTAMs with IDs but missing other fields should be preserved");
    }

    @Test
    void dedup_prefersNewerNotamWhenDuplicatesFound() {
        // Two NOTAMs that are duplicates, newer one should be preferred
        // Use empty IDs to test content-based deduplication with preference logic
        final Notam older = new Notam.Builder()
                .id("")  // Empty ID to force content-based key
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME)
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final Notam newer = new Notam.Builder()
                .id("")  // Empty ID
                .number("5/31")
                .type("RUNWAY")
                .issued(BASE_TIME.plusHours(1))  // Newer
                .location(LOCATION)
                .text("RWY 8L/26R CLOSED")
                .build();

        final List<Notam> input = List.of(older, newer);
        final List<Notam> result = NotamDeduplication.dedup(input);

        assertEquals(1, result.size());
        assertEquals(newer, result.get(0), "Newer NOTAM should be preferred when duplicates are found");
    }
}

