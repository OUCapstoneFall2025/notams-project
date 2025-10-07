package ou.capstone.notams.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class AirportValidatorTest {
    @Test
    void validatesIataAndIcao() {
        AirportValidator v = new AirportValidator();
        assertTrue(v.validate("LAX").isOk());
        assertTrue(v.validate("KSEA").isOk());
        assertFalse(v.validate("Some Random 123").isOk());
    }
}
