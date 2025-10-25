package ou.capstone.notams.api;

import ou.capstone.notams.Notam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Test for CCS-32
public class NotamParserDedupeTest {

    private static String featureJson(
            String id, String number, String type, String issuedIso, String icao,
            double lon, double lat, Double radius, String text
    ) {
        StringBuilder g = new StringBuilder();
        g.append("{")
         .append("\"type\":\"Point\",")
         .append("\"coordinates\":[").append(lon).append(",").append(lat).append("]");
        if (radius != null) {
            g.append(",\"radius\":").append(radius);
        }
        g.append("}");

        String notam =
            "{"
                + "\"id\":\"" + id + "\","
                + "\"number\":\"" + number + "\","
                + "\"type\":\"" + type + "\","
                + "\"issued\":\"" + issuedIso + "\","
                + "\"Location\":\"" + icao + "\","
                + "\"text\":\"" + escape(text) + "\""
            + "}";

        return "{"
                + "\"properties\":{"
                    + "\"coreNOTAMData\":{"
                        + "\"notam\":" + notam
                    + "}"
                + "},"
                + "\"geometry\":" + g
            + "}";
    }

    private static String docWithItems(String... featuresJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");
        for (int i = 0; i < featuresJson.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(featuresJson[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Test
    void keepsNewerWhenSameId() {
        NotamParser parser = new NotamParser();

        // Two features share the SAME id -> dedupe key by id; newer one should win
        String f1 = featureJson(
                "A123", "5/31", "N",
                "2025-10-25T10:00:00Z", "KJFK",
                -73.7781, 40.6413, 5.0,
                "RUNWAY CLOSED EARLIER VERSION"
        );
        String f2 = featureJson(
                "A123", "5/31", "N",
                "2025-10-25T12:00:00Z", "KJFK",
                -73.7781, 40.6413, 5.0,
                "RUNWAY CLOSED NEWER VERSION"
        );

        String json = docWithItems(f1, f2);

        List<Notam> out = parser.parseGeoJson(json);
        assertEquals(1, out.size(), "Duplicates by id should collapse to one");

        Notam kept = out.get(0);
        assertEquals("A123", kept.getId());
        assertEquals("RUNWAY CLOSED NEWER VERSION", kept.getText(), "Newer issued should be preferred");
    }

    @Test
    void prefersRadiusWhenIssuedEqual() {
        NotamParser parser = new NotamParser();

        // Same id and same issued time; one has radius, the other doesn't -> prefer the one with radius
        String f1 = featureJson(
                "B999", "7/10", "N",
                "2025-10-25T12:00:00Z", "KLAX",
                -118.4085, 33.9416, null,
                "AIRSPACE RESTRICTION"
        );
        String f2 = featureJson(
                "B999", "7/10", "N",
                "2025-10-25T12:00:00Z", "KLAX",
                -118.4085, 33.9416, 10.0,
                "AIRSPACE RESTRICTION WITH RADIUS"
        );

        String json = docWithItems(f1, f2);

        List<Notam> out = parser.parseGeoJson(json);
        assertEquals(1, out.size());

        Notam kept = out.get(0);
        assertEquals("B999", kept.getId());
        assertEquals("AIRSPACE RESTRICTION WITH RADIUS", kept.getText(), "Should prefer item that has radius");
        assertNotNull(kept.getRadiusNm());
        assertEquals(10.0, kept.getRadiusNm(), 1e-9);
    }

    @Test
    void preservesOrderForDistinctItems() {
        NotamParser parser = new NotamParser();

        // Distinct ids -> no dedupe; must preserve original order
        String f1 = featureJson(
                "C001", "1/01", "N",
                "2025-10-25T09:00:00Z", "KSEA",
                -122.3088, 47.4502, 3.0,
                "TAXIWAY CLOSURE"
        );
        String f2 = featureJson(
                "C002", "1/02", "N",
                "2025-10-25T09:05:00Z", "KSEA",
                -122.3088, 47.4502, 3.0,
                "APRON WORK IN PROGRESS"
        );
        String f3 = featureJson(
                "C003", "1/03", "N",
                "2025-10-25T09:10:00Z", "KSEA",
                -122.3088, 47.4502, 3.0,
                "RUNWAY LIGHTS U/S"
        );

        String json = docWithItems(f1, f2, f3);

        List<Notam> out = parser.parseGeoJson(json);
        assertEquals(3, out.size(), "No duplicates expected");
        assertEquals("C001", out.get(0).getId());
        assertEquals("C002", out.get(1).getId());
        assertEquals("C003", out.get(2).getId());
    }

    @Test
    void tieBreakLongerTextWhenAllElseEqual() {
        NotamParser parser = new NotamParser();

        String f1 = featureJson(
                "D777", "2/22", "N",
                "2025-10-25T08:00:00Z", "KDFW",
                -97.0403, 32.8998, 2.0,
                "SHORT"
        );
        String f2 = featureJson(
                "D777", "2/22", "N",
                "2025-10-25T08:00:00Z", "KDFW",
                -97.0403, 32.8998, 2.0,
                "THIS TEXT IS LONGER AND SHOULD WIN"
        );

        String json = docWithItems(f1, f2);

        List<Notam> out = parser.parseGeoJson(json);
        assertEquals(1, out.size());
        assertEquals("THIS TEXT IS LONGER AND SHOULD WIN", out.get(0).getText());
    }
}
