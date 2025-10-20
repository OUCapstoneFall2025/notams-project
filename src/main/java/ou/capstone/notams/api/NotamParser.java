package ou.capstone.notams.api;

import ou.capstone.notams.Notam;
import ou.capstone.notams.route.Coordinate;
import com.fasterxml.jackson.core.JsonProcessingException;
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

        logger.info("API Response preview: {}", geoJsonResponse.substring(0, Math.min(200, geoJsonResponse.length())));

        if (geoJsonResponse == null || geoJsonResponse.trim().isEmpty()) {
            logger.warn("Empty or null GeoJSON response provided");
            return new ArrayList<>();
        }

        logger.debug("Raw GeoJSON response length: {} characters", geoJsonResponse.length());

        try {
            JsonNode root = objectMapper.readTree(geoJsonResponse);
            logger.debug("Successfully parsed root JSON object");

            if (!root.has("items")) {
                logger.warn("GeoJSON response missing 'items' array");
                return new ArrayList<>();
            }

            JsonNode items = root.get("items");
            if (!items.isArray()) {
                logger.warn("'items' is not an array");
                return new ArrayList<>();
            }

            logger.info("Found {} items in GeoJSON response", items.size());

            final List<Notam> notams = new ArrayList<>();
            int successfullyParsed = 0;
            int skipped = 0;

            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonNode feature = items.get(i);
                    logger.debug("Processing feature {}/{}", i + 1, items.size());

                    Notam notam = parseFeature(feature);
                    if (notam != null) {
                        notams.add(notam);
                        successfullyParsed++;
                        logger.debug("Successfully parsed NOTAM: {}", notam.getId());
                    } else {
                        skipped++;
                        logger.debug("Skipped feature {}: missing required fields", i + 1);
                    }
                } catch (DateTimeParseException e) {
                    skipped++;
                    logger.warn("Failed to parse feature {} due to date parsing error: {}", i + 1, e.getMessage());
                } catch (Exception e) {
                    skipped++;
                    logger.warn("Failed to parse feature {} due to unexpected error: {}", i + 1, e.getMessage());
                }
            }

            logger.info("Parsing complete: {} NOTAMs parsed successfully, {} skipped", 
                        successfullyParsed, skipped);
            return notams;

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse GeoJSON response due to JSON processing error: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid GeoJSON response: " + e.getMessage(), e);
        } catch (final Exception e) {
            logger.error("Failed to parse GeoJSON response due to unexpected error: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid GeoJSON response: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a single GeoJSON feature into a Notam object.
     * Extracts data from properties.coreNOTAMData.notam and geometry.
     * 
     * @param feature the GeoJSON feature object
     * @return a Notam object, or null if required fields are missing or parsing fails.
     *         Returning null allows the main parser to skip invalid items and continue
     *         processing other valid NOTAMs in the collection.
     */
    private Notam parseFeature(JsonNode feature) {
        try {
            // Navigate to the coreNOTAMData.notam object with cleaner validation
            JsonNode notamData = navigateToNotamData(feature);
            if (notamData == null) {
                return null;
            }

            // Extract required fields with validation
            String id = extractRequiredField(notamData, "id");
            String number = extractRequiredField(notamData, "number");
            String issuedStr = extractRequiredField(notamData, "issued");
            
            if (id == null || number == null || issuedStr == null) {
                logger.debug("Feature missing required fields - id: {}, number: {}, issued: {}", 
                            id, number, issuedStr);
                return null;
            }

            OffsetDateTime issued = parseTimestamp(issuedStr);
            if (issued == null) {
                logger.debug("Failed to parse issued timestamp: {}", issuedStr);
                return null;
            }

            // Extract optional fields with sensible defaults
            String type = extractOptionalField(notamData, "type", "N");
            String icaoLocation = extractOptionalField(notamData, "icaoLocation", null);
            String text = extractOptionalField(notamData, "text", "");

            // Extract geometry (coordinates)
            Coordinate coordinates = extractCoordinates(feature);
            if (coordinates == null) {
                logger.debug("Feature has invalid/missing geometry, skipping NOTAM");
                return null;
            }
            double latitude = coordinates.latDeg;
            double longitude = coordinates.lonDeg;

            // Extract radius if available
            Double radiusNm = extractRadius(feature, feature.get("properties").get("coreNOTAMData"));

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

        } catch (DateTimeParseException e) {
            logger.warn("Error parsing feature due to date parsing error: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Error parsing feature due to unexpected error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Navigates to the notam data object with cleaner validation.
     * 
     * @param feature the GeoJSON feature object
     * @return the notam JsonNode, or null if navigation fails
     */
    private JsonNode navigateToNotamData(JsonNode feature) {
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

        return coreData.get("notam");
    }

    /**
     * Extracts a required field from the notam data.
     * 
     * @param notamData the notam JsonNode
     * @param fieldName the field name to extract
     * @return the field value as text, or null if missing
     */
    private String extractRequiredField(JsonNode notamData, String fieldName) {
        if (!notamData.has(fieldName)) {
            logger.debug("Missing required field: {}", fieldName);
            return null;
        }
        return notamData.get(fieldName).asText();
    }

    /**
     * Extracts an optional field from the notam data with a default value.
     * 
     * @param notamData the notam JsonNode
     * @param fieldName the field name to extract
     * @param defaultValue the default value if field is missing
     * @return the field value as text, or the default value if missing
     */
    private String extractOptionalField(JsonNode notamData, String fieldName, String defaultValue) {
        if (!notamData.has(fieldName)) {
            return defaultValue;
        }
        return notamData.get(fieldName).asText();
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
     * Handles both Point and GeometryCollection geometry types.
     * For GeometryCollection, extracts coordinates from the first Point geometry found.
     *
     * @param feature the GeoJSON feature object
     * @return Coordinate object with latitude and longitude, or null if geometry is missing/invalid.
     */
    private Coordinate extractCoordinates(JsonNode feature) {
        try {
            if (!feature.has("geometry")) {
                logger.warn("Feature missing 'geometry' field (required for coordinate extraction) - NOTAM will be skipped");
                return null;
            }

            JsonNode geometry = feature.get("geometry");
            if (!geometry.has("type")) {
                logger.warn("Geometry missing 'type' field - NOTAM will be skipped");
                return null;
            }

            String geometryType = geometry.get("type").asText();

            // Handle Point geometry
            if ("Point".equals(geometryType)) {
                return extractPointCoordinates(geometry);
            }

            // Handle GeometryCollection
            if ("GeometryCollection".equals(geometryType)) {
                return extractGeometryCollectionCoordinates(geometry);
            }

            logger.warn("Geometry type '{}' is not supported (only 'Point' and 'GeometryCollection' are supported) - NOTAM will be skipped",
                    geometryType);
            return null;

        } catch (ClassCastException e) {
            logger.warn("Failed to extract coordinates due to type conversion error: {} - NOTAM will be skipped",
                    e.getMessage());
            return null;
        } catch (NumberFormatException e) {
            logger.warn("Failed to extract coordinates due to number format error: {} - NOTAM will be skipped",
                    e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error extracting coordinates ({}): {} - NOTAM will be skipped",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Extracts coordinates from a Point geometry.
     *
     * @param geometry the geometry JsonNode (must be type "Point")
     * @return Coordinate object, or null if extraction fails
     */
    private Coordinate extractPointCoordinates(JsonNode geometry) {
        if (!geometry.has("coordinates")) {
            logger.warn("Point geometry missing 'coordinates' field - NOTAM will be skipped");
            return null;
        }

        JsonNode coords = geometry.get("coordinates");
        if (!coords.isArray()) {
            logger.warn("Point geometry coordinates is not an array - NOTAM will be skipped");
            return null;
        }

        if (coords.size() < 2) {
            logger.warn("Point geometry coordinates array has {} elements, expected at least 2 - NOTAM will be skipped",
                    coords.size());
            return null;
        }

        // GeoJSON format is [longitude, latitude]
        double longitude = coords.get(0).asDouble();
        double latitude = coords.get(1).asDouble();

        return validateAndCreateCoordinate(latitude, longitude);
    }

    /**
     * Extracts coordinates from a GeometryCollection.
     * Searches for the first Point geometry in the collection and extracts its coordinates.
     *
     * @param geometry the geometry JsonNode (must be type "GeometryCollection")
     * @return Coordinate object from first Point found, or null if no valid Point exists
     */
    private Coordinate extractGeometryCollectionCoordinates(JsonNode geometry) {
        if (!geometry.has("geometries")) {
            logger.warn("GeometryCollection missing 'geometries' array - NOTAM will be skipped");
            return null;
        }

        JsonNode geometries = geometry.get("geometries");
        if (!geometries.isArray()) {
            logger.warn("GeometryCollection 'geometries' is not an array - NOTAM will be skipped");
            return null;
        }

        if (geometries.isEmpty()) {
            logger.warn("GeometryCollection has empty 'geometries' array - NOTAM will be skipped");
            return null;
        }

        // Find the first Point geometry in the collection
        for (int i = 0; i < geometries.size(); i++) {
            JsonNode geom = geometries.get(i);

            if (!geom.has("type")) {
                continue;
            }

            String geomType = geom.get("type").asText();

            if ("Point".equals(geomType)) {
                logger.debug("Found Point geometry at index {} in GeometryCollection", i);
                return extractPointCoordinates(geom);
            }
        }

        logger.warn("GeometryCollection contains no Point geometry - NOTAM will be skipped");
        return null;
    }

    /**
     * Validates coordinate ranges and creates a Coordinate object.
     *
     * @param latitude latitude in degrees
     * @param longitude longitude in degrees
     * @return Coordinate object, or null if validation fails
     */
    private Coordinate validateAndCreateCoordinate(double latitude, double longitude) {
        // Validate coordinate ranges
        if (latitude < -90 || latitude > 90) {
            logger.warn("Invalid latitude value: {} (must be between -90 and 90) - NOTAM will be skipped",
                    latitude);
            return null;
        }

        if (longitude < -180 || longitude > 180) {
            logger.warn("Invalid longitude value: {} (must be between -180 and 180) - NOTAM will be skipped",
                    longitude);
            return null;
        }

        logger.debug("Extracted coordinates: lat={}, lon={}", latitude, longitude);
        return new Coordinate(latitude, longitude);
    }

    /**
     * Attempts to extract a radius value from the NOTAM data.
     * The radius might be in different places depending on the NOTAM structure.
     * Priority: geometry radius takes precedence over notam data radius.
     * 
     * @param feature the GeoJSON feature object
     * @param coreData the coreNOTAMData object
     * @return radius in nautical miles, or null if not found
     */
    private Double extractRadius(JsonNode feature, JsonNode coreData) {
        // Try to find radius in various possible locations
        // This is a best-effort extraction as the FAA API structure may vary

        try {
            boolean hasGeometryRadius = false;
            boolean hasNotamRadius = false;
            Double geometryRadius = null;
            Double notamRadius = null;

            // Check if there's a radius property in geometry
            if (feature.has("geometry")) {
                JsonNode geometry = feature.get("geometry");
                if (geometry.has("radius")) {
                    geometryRadius = geometry.get("radius").asDouble();
                    hasGeometryRadius = true;
                    logger.debug("Found radius in geometry: {} NM", geometryRadius);
                }
            }

            // Check in coreNOTAMData.notam
            if (coreData.has("notam")) {
                JsonNode notamData = coreData.get("notam");
                if (notamData.has("radius")) {
                    notamRadius = notamData.get("radius").asDouble();
                    hasNotamRadius = true;
                    logger.debug("Found radius in notam data: {} NM", notamRadius);
                }
            }

            // Handle case where radius appears in both locations
            if (hasGeometryRadius && hasNotamRadius) {
                if (Math.abs(geometryRadius - notamRadius) > 0.001) { // Allow for small floating point differences
                    logger.warn("Conflicting radius values found: geometry={} NM, notam={} NM. Using geometry radius (geometry takes precedence).", 
                               geometryRadius, notamRadius);
                } else {
                    logger.debug("Radius found in both locations with consistent values: {} NM (using geometry)", 
                               geometryRadius);
                }
                return geometryRadius;
            }

            // Return the available radius
            if (hasGeometryRadius) {
                return geometryRadius;
            } else if (hasNotamRadius) {
                return notamRadius;
            }

            logger.debug("No radius found in NOTAM data");
            return null;

        } catch (Exception e) {
            logger.debug("Error extracting radius due to unexpected error: {}", e.getMessage());
            return null;
        }
    }
}

