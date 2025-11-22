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

import ou.capstone.notams.api.FaaNotamApiWrapper;

/**
 * Tests for GeoJsonReader functionality.
 * Includes both unit tests with mock data and integration tests with live API.
 */
final class GeoJsonReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonReaderTest.class);

    @Test
    void fetchesMockNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching mock NOTAMs via ConnectToAPI");
        
        // Load mock JSON from test resources
        final String mockJson = loadMockJsonFromResources();
        
        // Mock ConnectToAPI.fetchRawJson() to return mock JSON
        try (MockedStatic<FaaNotamApiWrapper> mockedConnectToAPI = Mockito.mockStatic(
				FaaNotamApiWrapper.class)) {
            mockedConnectToAPI.when(() -> FaaNotamApiWrapper.fetchRawJson(any(
							FaaNotamApiWrapper.QueryParamsBuilder.class)))
                    .thenReturn(mockJson);
            
            // Call the mocked method and parse
            final FaaNotamApiWrapper.QueryParamsBuilder queryParams = new FaaNotamApiWrapper.QueryParamsBuilder("KOKC");
            final String json = FaaNotamApiWrapper.fetchRawJson(queryParams);
            final List<Notam> notams = new GeoJsonReader().parseNotamsFromGeoJson(json);
            
            assertNotNull(notams, "Parsed list should not be null");
            assertFalse(notams.isEmpty(), "Should have at least one mock NOTAM");
            logger.info("Successfully parsed {} mock NOTAMs", notams.size());
            
            // Verify the mock was called
            mockedConnectToAPI.verify(() -> FaaNotamApiWrapper.fetchRawJson(any(
					FaaNotamApiWrapper.QueryParamsBuilder.class)), times(1));
        }
    }

    @Test
    @Tag("integration")
    @Disabled("Integration test - run with integrationTest task")
    void fetchesLiveNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching live NOTAMs via ConnectToAPI");
        // This test requires live API access and should be run with integrationTest task
        // This test calls ConnectToAPI directly without mocking
        final FaaNotamApiWrapper.QueryParamsBuilder queryParams = new FaaNotamApiWrapper.QueryParamsBuilder("KOKC");
        final String json = FaaNotamApiWrapper.fetchRawJson(queryParams);
        final List<Notam> notams = new GeoJsonReader().parseNotamsFromGeoJson(json);
        assertNotNull(notams, "Parsed list should not be null");
    }

    /**
     * Loads mock NOTAM JSON data from test resources.
     * Returns the raw JSON string.
     *
     * @return The raw JSON string from the mock data file
     * @throws Exception if the mock data file cannot be read
     */
    private String loadMockJsonFromResources() throws Exception {
        try (final InputStream inputStream = GeoJsonReaderTest.class.getClassLoader()
                .getResourceAsStream("mock-faa-response.json")) {
            
            if (inputStream == null) {
                throw new IllegalStateException("Mock data file 'mock-faa-response.json' not found in test resources");
            }
            
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
