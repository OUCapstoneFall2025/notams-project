package ou.capstone.notams;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotamTest {

    @Test
    void constructsAndExposesFields() {
        Notam n = new Notam(
                "N555", "5/31", "RUNWAY",
                OffsetDateTime.parse("2025-09-28T12:00:00Z"),
                "KATL", 33.6407, -84.4277, 3.0,
                "RWY 8L/26R CLOSED"
        );

        assertEquals("N555", n.getId());
        assertEquals("5/31", n.getNumber());
        assertEquals("RUNWAY", n.getType());
        assertEquals("KATL", n.getLocation());
        assertEquals(33.6407, n.getLatitude(), 1e-6);
        assertEquals(-84.4277, n.getLongitude(), 1e-6);
        assertEquals(3.0, n.getRadiusNm(), 1e-6);
        assertTrue(n.getText().contains("CLOSED"));
    }
}
