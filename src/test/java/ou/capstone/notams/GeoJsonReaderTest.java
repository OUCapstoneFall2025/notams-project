package ou.capstone.notams;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GeoJsonReader functionality.
 * Includes both unit tests with mock data and integration tests with live API.
 */
final class GeoJsonReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonReaderTest.class);

    @Test
    void fetchesMockNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching mock NOTAMs via ConnectToAPI");
        
        // Load mock data from test resources
        final List<Notam> mockNotams = loadMockNotamsFromResources();
        
        // Mock ConnectToAPI.fetchNotams() to return mock data
        try (MockedStatic<ConnectToAPI> mockedConnectToAPI = Mockito.mockStatic(ConnectToAPI.class)) {
            mockedConnectToAPI.when(ConnectToAPI::fetchNotams)
                    .thenReturn(mockNotams);
            
            // Call the mocked method
            List<Notam> notams = ConnectToAPI.fetchNotams();
            
            assertNotNull(notams, "Parsed list should not be null");
            assertFalse(notams.isEmpty(), "Should have at least one mock NOTAM");
            logger.info("Successfully parsed {} mock NOTAMs", notams.size());
            
            // Verify the mock was called
            mockedConnectToAPI.verify(ConnectToAPI::fetchNotams, times(1));
        }
    }

    @Test
    @Tag("integration")
    @Disabled("Integration test - run with integrationTest task")
    void fetchesLiveNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching live NOTAMs via ConnectToAPI");
        // This test requires live API access and should be run with integrationTest task
        // which sets ConnectToApi.UseLiveData system property
        // This test calls ConnectToAPI directly without mocking
        List<Notam> notams = ConnectToAPI.fetchNotams();
        assertNotNull(notams, "Parsed list should not be null");
    }

    /**
     * Loads mock NOTAM data from test resources.
     * This helper method reads the mock-faa-response.json file and parses it.
     *
     * @return List of parsed Notam objects from the mock data
     * @throws Exception if the mock data file cannot be read or parsed
     */
    private List<Notam> loadMockNotamsFromResources() throws Exception {
        try (final InputStream inputStream = GeoJsonReaderTest.class.getClassLoader()
                .getResourceAsStream("mock-faa-response.json")) {
            
            if (inputStream == null) {
                throw new IllegalStateException("Mock data file 'mock-faa-response.json' not found in test resources");
            }
            
            final String mockJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new GeoJsonReader().parseNotamsFromGeoJson(mockJson);
        }
    }
}