package ou.capstone.notams.api;

import ou.capstone.notams.Notam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotamParserTest {

    private NotamParser parser;

    @BeforeEach
    void setUp() {
        parser = new NotamParser();
    }

    @Test
    void testParseValidGeoJson() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N123456",
                        "number": "10/182",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "location": "DFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "RWY 13R/31L CLSD"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        Notam notam = notams.get(0);
        
        assertEquals("N123456", notam.getId());
        assertEquals("10/182", notam.getNumber());
        assertEquals("N", notam.getType());
        assertEquals("KDFW", notam.getLocation());
        assertEquals(32.8998, notam.getLatitude(), 0.0001);
        assertEquals(-97.0403, notam.getLongitude(), 0.0001);
        assertNull(notam.getRadiusNm());
        assertEquals("RWY 13R/31L CLSD", notam.getText());
        assertNotNull(notam.getIssued());
    }

    @Test
    void testParseMultipleFeatures() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N001",
                        "number": "1/10",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "Test NOTAM 1"
                      }
                    }
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.6007, 35.3931]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N002",
                        "number": "2/10",
                        "type": "R",
                        "icaoLocation": "KOKC",
                        "issued": "2025-10-02T14:00:00Z",
                        "text": "Test NOTAM 2"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(2, notams.size());
        assertEquals("N001", notams.get(0).getId());
        assertEquals("N002", notams.get(1).getId());
    }

    @Test
    void testParseEmptyFeatureCollection() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": []
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseNullResponse() {
        List<Notam> notams = parser.parseGeoJson(null);
        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseEmptyString() {
        List<Notam> notams = parser.parseGeoJson("");
        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThrows(IllegalArgumentException.class, () -> {
            parser.parseGeoJson(invalidJson);
        });
    }

    @Test
    void testParseMissingRequiredFields() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "text": "Missing id, number, and issued"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        // Should skip this feature due to missing required fields
        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseWithRadiusInGeometry() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998],
                    "radius": 5.0
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N789",
                        "number": "5/20",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "Test with radius"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertEquals(5.0, notams.get(0).getRadiusNm());
    }

    @Test
    void testParseWithRadiusInNotamData() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N789",
                        "number": "5/20",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "radius": 10.0,
                        "text": "Test with radius in notam"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertEquals(10.0, notams.get(0).getRadiusNm());
    }

    @Test
    void testParseMissingGeometry() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N456",
                        "number": "3/15",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "Missing geometry"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        // Should still parse but with default coordinates (0, 0)
        assertEquals(1, notams.size());
        assertEquals(0.0, notams.get(0).getLatitude());
        assertEquals(0.0, notams.get(0).getLongitude());
    }

    @Test
    void testParsePartiallyValidFeatures() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N111",
                        "number": "1/1",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "Valid NOTAM"
                      }
                    }
                  }
                },
                {
                  "type": "Feature",
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "type": "N",
                        "text": "Missing required fields"
                      }
                    }
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.6007, 35.3931]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N222",
                        "number": "2/2",
                        "type": "R",
                        "icaoLocation": "KOKC",
                        "issued": "2025-10-02T14:00:00Z",
                        "text": "Another valid NOTAM"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        // Should parse 2 valid NOTAMs and skip the invalid one
        assertEquals(2, notams.size());
        assertEquals("N111", notams.get(0).getId());
        assertEquals("N222", notams.get(1).getId());
    }

    @Test
    void testParseWithDifferentTimestampFormats() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N123",
                        "number": "1/1",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00+00:00",
                        "text": "With timezone offset"
                      }
                    }
                  }
                }
              ]
            }
            """;

        List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertNotNull(notams.get(0).getIssued());
    }
}

