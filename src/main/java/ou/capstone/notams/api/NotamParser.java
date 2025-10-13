package ou.capstone.notams.api;

import ou.capstone.notams.Notam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses FAA GeoJSON responses and converts them into Notam objects.
 * CCS-31: Responsible for extracting key NOTAM properties from API responses.
 */
public class NotamParser {
    private static final Logger logger = LoggerFactory.getLogger(NotamParser.class);
    private final ObjectMapper objectMapper;

    public NotamParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parses a FAA GeoJSON response string and extracts a list of Notam objects.
     * 
     * @param geoJsonResponse the GeoJSON response string from the FAA API
     * @return list of parsed Notam objects
     * @throws IllegalArgumentException if the JSON is malformed or missing required fields
     */
    public List<Notam> parseGeoJson(String geoJsonResponse) {
        logger.info("Starting GeoJSON parsing");

        if (geoJsonResponse == null || geoJsonResponse.trim().isEmpty()) {
            logger.warn("Empty or null GeoJSON response provided");
            return new ArrayList<>();
        }

        logger.debug("Raw GeoJSON response length: {} characters", geoJsonResponse.length());

        try {
            JsonNode root = objectMapper.readTree(geoJsonResponse);
            logger.debug("Successfully parsed root JSON object");

            if (!root.has("features")) {
                logger.warn("GeoJSON response missing 'features' array");
                return new ArrayList<>();
            }

            JsonNode features = root.get("features");
            if (!features.isArray()) {
                logger.warn("'features' is not an array");
                return new ArrayList<>();
            }

            logger.info("Found {} features in GeoJSON response", features.size());

            List<Notam> notams = new ArrayList<>();
            int successfullyParsed = 0;
            int skipped = 0;

            for (int i = 0; i < features.size(); i++) {
                try {
                    JsonNode feature = features.get(i);
                    logger.debug("Processing feature {}/{}", i + 1, features.size());

                    Notam notam = parseFeature(feature);
                    if (notam != null) {
                        notams.add(notam);
                        successfullyParsed++;
                        logger.debug("Successfully parsed NOTAM: {}", notam.getId());
                    } else {
                        skipped++;
                        logger.debug("Skipped feature {}: missing required fields", i + 1);
                    }
                } catch (Exception e) {
                    skipped++;
                    logger.warn("Failed to parse feature {}: {}", i + 1, e.getMessage());
                }
            }

            logger.info("Parsing complete: {} NOTAMs parsed successfully, {} skipped", 
                        successfullyParsed, skipped);
            return notams;

        } catch (Exception e) {
            logger.error("Failed to parse GeoJSON response: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid GeoJSON response: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a single GeoJSON feature into a Notam object.
     * Extracts data from properties.coreNOTAMData.notam and geometry.
     * 
     * @param feature the GeoJSON feature object
     * @return a Notam object, or null if required fields are missing
     */
    private Notam parseFeature(JsonNode feature) {
        try {
            // Navigate to the coreNOTAMData.notam object
            if (!feature.has("properties")) {
                logger.debug("Feature missing 'properties' field");
                return null;
            }

            JsonNode properties = feature.get("properties");
            if (!properties.has("coreNOTAMData")) {
                logger.debug("Properties missing 'coreNOTAMData' field");
                return null;
            }

            JsonNode coreData = properties.get("coreNOTAMData");
            if (!coreData.has("notam")) {
                logger.debug("CoreNOTAMData missing 'notam' field");
                return null;
            }

            JsonNode notamData = coreData.get("notam");

            // Extract required fields
            String id = notamData.has("id") ? notamData.get("id").asText() : null;
            String number = notamData.has("number") ? notamData.get("number").asText() : null;
            String type = notamData.has("type") ? notamData.get("type").asText() : "UNKNOWN";
            String icaoLocation = notamData.has("icaoLocation") ? notamData.get("icaoLocation").asText() : "UNKNOWN";
            String text = notamData.has("text") ? notamData.get("text").asText() : "";

            // Issued timestamp
            String issuedStr = notamData.has("issued") ? notamData.get("issued").asText() : null;
            OffsetDateTime issued = parseTimestamp(issuedStr);

            // Extract geometry (coordinates)
            double[] coordinates = extractCoordinates(feature);
            double latitude = coordinates[0];
            double longitude = coordinates[1];

            // Extract radius if available (geometry type might be circle or point with radius)
            Double radiusNm = extractRadius(feature, coreData);

            // Validate required fields
            if (id == null || number == null || issued == null) {
                logger.debug("Feature missing required fields - id: {}, number: {}, issued: {}", 
                            id, number, issued);
                return null;
            }

            logger.debug("Parsed NOTAM - ID: {}, Number: {}, Type: {}, Location: {}", 
                        id, number, type, icaoLocation);

            return new Notam(
                id,
                number,
                type,
                issued,
                icaoLocation,
                latitude,
                longitude,
                radiusNm,
                text
            );

        } catch (Exception e) {
            logger.warn("Error parsing feature: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses a timestamp string into OffsetDateTime.
     * 
     * @param timestamp the timestamp string (ISO 8601 format expected)
     * @return OffsetDateTime object, or null if parsing fails
     */
    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            logger.debug("Timestamp is null or empty");
            return null;
        }

        try {
            OffsetDateTime result = OffsetDateTime.parse(timestamp);
            logger.debug("Successfully parsed timestamp: {}", timestamp);
            return result;
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse timestamp '{}': {}", timestamp, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts coordinates from the geometry field.
     * Handles Point geometry type.
     * 
     * @param feature the GeoJSON feature object
     * @return array of [latitude, longitude], defaults to [0.0, 0.0] if not found
     */
    private double[] extractCoordinates(JsonNode feature) {
        try {
            if (!feature.has("geometry")) {
                logger.debug("Feature missing 'geometry' field, using default coordinates");
                return new double[]{0.0, 0.0};
            }

            JsonNode geometry = feature.get("geometry");
            String geometryType = geometry.has("type") ? geometry.get("type").asText() : "";
            
            if (!"Point".equals(geometryType)) {
                logger.debug("Geometry type '{}' is not Point, using default coordinates", geometryType);
                return new double[]{0.0, 0.0};
            }

            if (!geometry.has("coordinates")) {
                logger.debug("Geometry missing 'coordinates' array");
                return new double[]{0.0, 0.0};
            }

            JsonNode coords = geometry.get("coordinates");
            if (coords.isArray() && coords.size() >= 2) {
                // GeoJSON format is [longitude, latitude]
                double longitude = coords.get(0).asDouble();
                double latitude = coords.get(1).asDouble();
                logger.debug("Extracted coordinates: lat={}, lon={}", latitude, longitude);
                return new double[]{latitude, longitude};
            }

            logger.debug("Coordinates array has insufficient elements");
            return new double[]{0.0, 0.0};

        } catch (Exception e) {
            logger.warn("Error extracting coordinates: {}", e.getMessage());
            return new double[]{0.0, 0.0};
        }
    }

    /**
     * Attempts to extract a radius value from the NOTAM data.
     * The radius might be in different places depending on the NOTAM structure.
     * 
     * @param feature the GeoJSON feature object
     * @param coreData the coreNOTAMData object
     * @return radius in nautical miles, or null if not found
     */
    private Double extractRadius(JsonNode feature, JsonNode coreData) {
        // Try to find radius in various possible locations
        // This is a best-effort extraction as the FAA API structure may vary

        try {
            // Check if there's a radius property in geometry
            if (feature.has("geometry")) {
                JsonNode geometry = feature.get("geometry");
                if (geometry.has("radius")) {
                    double radius = geometry.get("radius").asDouble();
                    logger.debug("Found radius in geometry: {} NM", radius);
                    return radius;
                }
            }

            // Check in coreNOTAMData.notam
            if (coreData.has("notam")) {
                JsonNode notamData = coreData.get("notam");
                if (notamData.has("radius")) {
                    double radius = notamData.get("radius").asDouble();
                    logger.debug("Found radius in notam data: {} NM", radius);
                    return radius;
                }
            }

            logger.debug("No radius found in NOTAM data");
            return null;

        } catch (Exception e) {
            logger.debug("Error extracting radius: {}", e.getMessage());
            return null;
        }
    }
}

