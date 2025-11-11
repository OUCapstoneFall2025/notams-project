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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        final Notam notam = notams.get(0);
        
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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseNullResponse() {
        final List<Notam> notams = parser.parseGeoJson(null);
        assertTrue(notams.isEmpty());
    }

    @Test
    void testParseEmptyString() {
        final List<Notam> notams = parser.parseGeoJson("");
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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertEquals(10.0, notams.get(0).getRadiusNm());
    }

    @Test
    void testParseWithRadiusInBothLocations() {
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
                        "radius": 10.0,
                        "text": "Test with radius in both locations"
                      }
                    }
                  }
                }
              ]
            }
            """;

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        // Should prioritize geometry radius over notam radius
        assertEquals(5.0, notams.get(0).getRadiusNm());
    }

    @Test
    void testParseWithConflictingRadiusValues() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-97.0403, 32.8998],
                    "radius": 3.0
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N999",
                        "number": "6/25",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "radius": 7.0,
                        "text": "Test with conflicting radius values"
                      }
                    }
                  }
                }
              ]
            }
            """;

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        // Should prioritize geometry radius even when values conflict
        assertEquals(3.0, notams.get(0).getRadiusNm());
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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertNull(notams.get(0).getLatitude());
        assertNull(notams.get(0).getLongitude());
        assertEquals("N456", notams.get(0).getId());
    }

    @Test
    void testParseInvalidCoordinateRanges() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [200.0, 95.0]
                  },
                  "properties": {
                    "coreNOTAMData": {
                      "notam": {
                        "id": "N789",
                        "number": "4/20",
                        "type": "N",
                        "icaoLocation": "KDFW",
                        "issued": "2025-10-01T12:00:00Z",
                        "text": "Invalid coordinates"
                      }
                    }
                  }
                }
              ]
            }
            """;

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        // Should preserve NOTAM with null coordinates for flight safety
        assertEquals(1, notams.size());
        assertNull(notams.get(0).getLatitude());
        assertNull(notams.get(0).getLongitude());
        assertEquals("N789", notams.get(0).getId());
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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

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

        final List<Notam> notams = parser.parseGeoJson(geoJson);

        assertEquals(1, notams.size());
        assertNotNull(notams.get(0).getIssued());
    }
}

