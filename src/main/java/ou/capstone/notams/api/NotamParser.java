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
    // Track the source of the last coordinate extraction
    private enum CoordinateSource { GEOMETRY, TEXT, NONE }
    private CoordinateSource lastCoordinateSource = CoordinateSource.NONE;

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

        if (geoJsonResponse == null || geoJsonResponse.isBlank()) {
            logger.warn("Empty or null GeoJSON response provided");
            return List.of();
        }
        logger.info("API Response preview: {}", geoJsonResponse.substring(0, Math.min(200, geoJsonResponse.length())));

        logger.debug("Raw GeoJSON response length: {} characters", geoJsonResponse.length());

        try {
            final JsonNode root = objectMapper.readTree(geoJsonResponse);
            logger.debug("Successfully parsed root JSON object");

            if (!root.has("items")) {
                logger.warn("GeoJSON response missing 'items' array");
                return new ArrayList<>();
            }

            final JsonNode items = root.get("items");
            if (!items.isArray()) {
                logger.warn("'items' is not an array");
                return new ArrayList<>();
            }

            logger.info("Found {} items in GeoJSON response", items.size());

            final List<Notam> notams = new ArrayList<>();
            int successfullyParsed = 0;
            int skipped = 0;
            int coordinatesFromGeometry = 0;
            int coordinatesFromText = 0;
            int noCoordinates = 0;

            for (int i = 0; i < items.size(); i++) {
                try {
                    final JsonNode feature = items.get(i);
                    logger.debug("Processing feature {}/{}", i + 1, items.size());

                    final Notam notam = parseFeature(feature);
                    if (notam != null) {
                        notams.add(notam);
                        successfullyParsed++;
                        logger.debug("Successfully parsed NOTAM: {}", notam.getId());

                        // Track coordinate source statistics based on extraction result
                        switch (lastCoordinateSource) {
                            case GEOMETRY:
                                coordinatesFromGeometry++;
                                break;
                            case TEXT:
                                coordinatesFromText++;
                                break;
                            case NONE:
                                noCoordinates++;
                                break;
                        }
                    } else {
                        skipped++;
                        logger.debug("Skipped feature {}: missing required fields", i + 1);
                    }
                } catch (final DateTimeParseException e) {
                    skipped++;
                    logger.warn("Failed to parse feature {} due to date parsing error: {}", i + 1, e.getMessage());
                } catch (final Exception e) {
                    skipped++;
                    logger.warn("Failed to parse feature {} due to unexpected error: {}", i + 1, e.getMessage());
                }
            }

            logger.info("Parsing complete: {} NOTAMs parsed successfully, {} skipped", successfullyParsed, skipped);
            logger.info("Coordinate sources: {} from geometry, {} from text, {} with no coordinates",
                    coordinatesFromGeometry, coordinatesFromText, noCoordinates);
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
            final JsonNode notamData = navigateToNotamData(feature);
            if (notamData == null) {
                return null;
            }

            // Extract required fields with validation
            final String id = extractRequiredField(notamData, "id");
            final String number = extractRequiredField(notamData, "number");
            final String issuedStr = extractRequiredField(notamData, "issued");

            if (id == null || number == null || issuedStr == null) {
                logger.debug("Feature missing required fields - id: {}, number: {}, issued: {}",
                        id, number, issuedStr);
                return null;
            }

            final OffsetDateTime issued = parseTimestamp(issuedStr);
            if (issued == null) {
                logger.debug("Failed to parse issued timestamp: {}", issuedStr);
                return null;
            }

            // Extract optional fields with sensible defaults
            final String type = extractOptionalField(notamData, "type", "N");
            final String icaoLocation = extractOptionalField(notamData, "icaoLocation", null);
            final String text = extractOptionalField(notamData, "text", "");

            // Extract coordinates using unified method (no 0,0 fallback)
            final Coordinate coordinates = extractCoordinatesFromFeature(feature);
            final Double radiusNm = extractRadius(feature, feature.get("properties").get("coreNOTAMData"));
            if (coordinates == null) {
                logger.warn("NOTAM {} has no parseable geometry. Text preserved for flight safety: {}",
                        id, text.substring(0, Math.min(100, text.length())));
                // Create NOTAM with explicit sentinel values to indicate "no valid coordinates"
                return new Notam(
                        id,
                        number,
                        type,
                        issued,
                        icaoLocation,
                        999.0,
                        999.0,
                        radiusNm,
                        text
                );
            }

            return new Notam(
                    id,
                    number,
                    type,
                    issued,
                    icaoLocation,
                    coordinates.latDeg,
                    coordinates.lonDeg,
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
     * Attempts to extract coordinates from NOTAM text when geometry is missing.
     * Looks for patterns like "PSN 5728N 1038E" (position in degrees/minutes).
     *
     * @param text the NOTAM text field
     * @return Coordinate object if pattern found, null otherwise
     */
    private Coordinate extractCoordinatesFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "PSN\\s+(\\d{4}(?:\\.\\d+)?)([NS])\\s+(\\d{4,5}(?:\\.\\d+)?)([EW])",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        final java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                final String latStr = matcher.group(1);
                final String latDir = matcher.group(2);
                final String lonStr = matcher.group(3);
                final String lonDir = matcher.group(4);
                final double latDegrees = Integer.parseInt(latStr.substring(0, 2));
                final double latMinutes = Double.parseDouble(latStr.substring(2));
                double latitude = latDegrees + (latMinutes / 60.0);
                if ("S".equalsIgnoreCase(latDir)) {
                    latitude = -latitude;
                }
                final int lonDegEnd = lonStr.length() >= 5 ? 3 : 2;
                final double lonDegrees = Integer.parseInt(lonStr.substring(0, lonDegEnd));
                final double lonMinutes = Double.parseDouble(lonStr.substring(lonDegEnd));
                double longitude = lonDegrees + (lonMinutes / 60.0);
                if ("W".equalsIgnoreCase(lonDir)) {
                    longitude = -longitude;
                }
                logger.debug("Extracted coordinates from text: lat={}, lon={}", latitude, longitude);
                return validateAndCreateCoordinate(latitude, longitude);
            } catch (final Exception e) {
                logger.debug("Failed to parse coordinates from text: {}", e.getMessage());
                return null;
            }
        }
        return null;
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
        final JsonNode properties = feature.get("properties");
        if (!properties.has("coreNOTAMData")) {
            logger.debug("Properties missing 'coreNOTAMData' field");
            return null;
        }
        final JsonNode coreData = properties.get("coreNOTAMData");
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
            final OffsetDateTime result = OffsetDateTime.parse(timestamp);
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
    /**
     * Extracts coordinates from a feature, trying multiple strategies.
     * Strategy order:
     * 1) Geometry
     * 2) Text (NOTAM 'text' field)
     * Returns null if neither yields coordinates (avoid 0,0 "Null Island").
     */
    private Coordinate extractCoordinatesFromFeature(final JsonNode feature) {
        // Strategy 1: Try geometry
        final Coordinate geometryCoords = extractCoordinatesFromGeometry(feature);
        if (geometryCoords != null) {
            lastCoordinateSource = CoordinateSource.GEOMETRY;
            return geometryCoords;
        }

        // Strategy 2: Try text
        final JsonNode notamData = navigateToNotamData(feature);
        if (notamData != null && notamData.has("text")) {
            final String text = notamData.get("text").asText();
            final Coordinate textCoords = extractCoordinatesFromText(text);
            if (textCoords != null) {
                lastCoordinateSource = CoordinateSource.TEXT;
                if (notamData.has("id")) {
                    logger.info("Extracted coordinates from text for NOTAM {}", notamData.get("id").asText());
                } else {
                    logger.info("Extracted coordinates from text for NOTAM (no id field present)");
                }
                return textCoords;
            }
        }

        // No coords found
        lastCoordinateSource = CoordinateSource.NONE;
        return null;
    }

    /**
     * Extracts coordinates strictly from the geometry field.
     * (Renamed from extractCoordinates for clarity.)
     */
    private Coordinate extractCoordinatesFromGeometry(final JsonNode feature) {
        try {
            if (!feature.has("geometry")) {
                logger.debug("Feature missing 'geometry' field");
                return null;
            }
            final JsonNode geometry = feature.get("geometry");
            if (!geometry.has("type")) {
                logger.debug("Geometry missing 'type' field");
                return null;
            }
            final String geometryType = geometry.get("type").asText();
            if ("Point".equals(geometryType)) {
                return extractPointCoordinates(geometry);
            }
            if ("GeometryCollection".equals(geometryType)) {
                return extractGeometryCollectionCoordinates(geometry);
            }
            logger.debug("Geometry type '{}' is not supported", geometryType);
            return null;
        } catch (final ClassCastException e) {
            logger.debug("Failed to extract coordinates due to type conversion error: {}", e.getMessage());
            return null;
        } catch (final NumberFormatException e) {
            logger.debug("Failed to extract coordinates due to number format error: {}", e.getMessage());
            return null;
        } catch (final Exception e) {
            logger.debug("Unexpected error extracting coordinates: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts coordinates from a Point geometry.
     *
     * @param geometry the geometry JsonNode (must be type "Point")
     * @return Coordinate object, or null if extraction fails
     */
    private Coordinate extractPointCoordinates(final JsonNode geometry) {
        if (!geometry.has("coordinates")) {
            logger.debug("Point geometry missing 'coordinates' field");
            return null;
        }
        final JsonNode coords = geometry.get("coordinates");
        if (!coords.isArray()) {
            logger.debug("Point geometry coordinates is not an array");
            return null;
        }
        if (coords.size() < 2) {
            logger.debug("Point geometry coordinates array has {} elements, expected at least 2", coords.size());
            return null;
        }
        // GeoJSON format is [longitude, latitude]
        final double longitude = coords.get(0).asDouble();
        final double latitude = coords.get(1).asDouble();
        return validateAndCreateCoordinate(latitude, longitude);
    }

    /**
     * Extracts coordinates from a GeometryCollection.
     * Searches for the first Point geometry in the collection and extracts its coordinates.
     *
     * Note: For NOTAM purposes, we only need a single representative coordinate.
     * GeometryCollections with multiple Points are rare; we use the first valid one found.
     *
     * @param geometry the geometry JsonNode (must be type "GeometryCollection")
     * @return Coordinate object from first Point found, or null if no valid Point exists
     */
    private Coordinate extractGeometryCollectionCoordinates(final JsonNode geometry) {
        if (!geometry.has("geometries")) {
            logger.debug("GeometryCollection missing 'geometries' array");
            return null;
        }
        final JsonNode geometries = geometry.get("geometries");
        if (!geometries.isArray()) {
            logger.debug("GeometryCollection 'geometries' is not an array");
            return null;
        }
        if (geometries.isEmpty()) {
            logger.debug("GeometryCollection has empty 'geometries' array");
            return null;
        }
        // Find the first Point geometry in the collection
        for (int i = 0; i < geometries.size(); i++) {
            final JsonNode geom = geometries.get(i);
            if (!geom.has("type")) {
                continue;
            }
            final String geomType = geom.get("type").asText();
            if ("Point".equals(geomType)) {
                logger.debug("Found Point geometry at index {} in GeometryCollection", i);
                return extractPointCoordinates(geom);
            }
        }
        logger.debug("GeometryCollection contains no Point geometry");
        return null;
    }

    /**
     * Validates coordinate ranges and creates a Coordinate object.
     *
     * @param latitude latitude in degrees
     * @param longitude longitude in degrees
     * @return Coordinate object, or null if validation fails
     */
    private Coordinate validateAndCreateCoordinate(final double latitude, final double longitude) {
        // Validate coordinate ranges
        if (latitude < -90 || latitude > 90) {
            logger.debug("Invalid latitude value: {} (must be between -90 and 90)", latitude);
            return null;
        }
        if (longitude < -180 || longitude > 180) {
            logger.debug("Invalid longitude value: {} (must be between -180 and 180)", longitude);
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
                final JsonNode geometry = feature.get("geometry");
                if (geometry.has("radius")) {
                    geometryRadius = geometry.get("radius").asDouble();
                    hasGeometryRadius = true;
                    logger.debug("Found radius in geometry: {} NM", geometryRadius);
                }
            }

            // Check in coreNOTAMData.notam
            if (coreData.has("notam")) {
                final JsonNode notamData = coreData.get("notam");
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