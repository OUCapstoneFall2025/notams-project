package ou.capstone.notams;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeoJsonReader functionality.
 * Includes both unit tests with mock data and integration tests with live API.
 */
final class GeoJsonReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonReaderTest.class);

    @Test
    void fetchesMockNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching mock NOTAMs via ConnectToAPI");
        List<Notam> notams = ConnectToAPI.fetchNotams();
        assertNotNull(notams, "Parsed list should not be null");
        assertFalse(notams.isEmpty(), "Should have at least one mock NOTAM");
        logger.info("Successfully parsed {} mock NOTAMs", notams.size());
    }

    @Test
    @Tag("integration")
    void fetchesLiveNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching live NOTAMs via ConnectToAPI");
        // This test requires live API access and should be run with integrationTest task
        // which doesn't set USE_MOCK_DATA environment variable
        List<Notam> notams = ConnectToAPI.fetchNotams();
        assertNotNull(notams, "Parsed list should not be null");
    }
}