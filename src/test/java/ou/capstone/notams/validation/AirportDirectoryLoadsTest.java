package ou.capstone.notams.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class AirportDirectoryLoadsTest {
    @Test
    void loadsCsvAndFindsKnownCodes() {
        AirportDirectory dir = new AirportDirectory();
        assertTrue(dir.findByIata("LAX").isPresent());
        assertTrue(dir.findByIcao("KJFK").isPresent());
    }
}
