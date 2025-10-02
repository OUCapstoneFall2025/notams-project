package ou.capstone.notams;

import ou.capstone.notams.Notam;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test that calls the FAA API through {@link ConnectToAPI}
 * and parses the live GeoJSON response into {@link Notam} objects
 * using {@link GeoJsonReader}.
 */
@Tag("integration")
final class GeoJsonReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonReaderTest.class);

    @Test
    @Tag("integration")
    void fetchesLiveNotams_viaConnectToAPI_andParses() throws Exception {
        logger.info("Fetching live NOTAMs via ConnectToAPI");
        List<Notam> notams = ConnectToAPI.fetchNotams();
        assertNotNull(notams, "Parsed list should not be null");
    }
}