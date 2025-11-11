package ou.capstone.notams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ou.capstone.notams.Notam;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.time.OffsetDateTime;

public class GeoJsonReader {
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_CORE_NOTAM_DATA = "coreNOTAMData";
    private static final String FIELD_NOTAM = "notam";
    private static final String FIELD_GEOMETRY = "geometry";
    private static final String FIELD_GEOMETRIES = "geometries";
    private static final String FIELD_COORDINATES = "coordinates";
    private static final String FIELD_RADIUS = "radius";

    // NOTAM field names
    private static final String FIELD_ID = "id";
    private static final String FIELD_NUMBER = "number";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ICAO_LOCATION = "icaoLocation";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_ISSUED = "issued";
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(GeoJsonReader.class);

    /**
     * Parses the FAA NOTAM GeoJSON response and converts its contents into a list of {@link Notam} objects.
     * <p>
     * This method reads the provided JSON string, accesses the "items" array from the root node,
     * and maps each item to a {@link Notam} instance using the {@code processItem} helper.
     * It does not perform validation or handle pagination metadata.
     *
     * @param json The full GeoJSON response as a string from the FAA NOTAM API.
     * @return A list of {@link Notam} objects parsed from the response.
     * @throws IOException If the input JSON is malformed or cannot be read.
     */

    public List<Notam> parseNotamsFromGeoJson(final String json) throws IOException {
        logger.info("Starting GeoJSON parse");
        final List<Notam> notams = new ArrayList<>();
        final JsonNode root = mapper.readTree(json);

        final JsonNode items = root.get(FIELD_ITEMS);
        if (items == null || !items.isArray()) {
            return notams;
        }

        for (final JsonNode item : items) {
            final Notam n = processItem(item);
            if (n != null) {
                notams.add(n);
                logger.debug("Processed NOTAM item {}", n.getId());
            } else {
                logger.warn("Skipped invalid NOTAM item - missing required fields");
            }
        }
        logger.info("Parsed {} NOTAMs from GeoJSON response", notams.size());
        return notams;
    }
    /**
     * Maps a single GeoJSON feature item into a {@link Notam}.
     * <p>
     * Expects the FAA item shape: properties → coreNOTAMData → notam, and (optionally) geometry
     * containing either {@code coordinates} or a {@code geometries[0].coordinates]} pair.
     * Performs minimal field extraction with no extra validation.
     *
     * @param item A GeoJSON feature object from the response "items" array.
     * @return A populated {@link Notam}, or {@code null} if required fields are missing.
     */
    private Notam processItem(final JsonNode item) {
        final JsonNode properties = item.get(FIELD_PROPERTIES);  // use constant
        if (properties == null) {
            logger.warn("Skipping item - missing '{}' field", FIELD_PROPERTIES);
            return null;
        }

        final JsonNode coreData = properties.get(FIELD_CORE_NOTAM_DATA);  // use constant
        if (coreData == null) {
            logger.warn("Skipping item - missing '{}' field", FIELD_CORE_NOTAM_DATA);
            return null;
        }

        final JsonNode notamNode = coreData.get(FIELD_NOTAM);  // use constant
        if (notamNode == null) {
            logger.warn("Skipping item - missing '{}' field", FIELD_NOTAM);
            return null;
        }

        // Use renamed methods and constants
        final String id = parseText(notamNode, FIELD_ID);
        final String number = parseText(notamNode, FIELD_NUMBER);
        final String type = parseText(notamNode, FIELD_TYPE);
        final String icaoLocation = parseText(notamNode, FIELD_ICAO_LOCATION);
        final String text = parseText(notamNode, FIELD_TEXT);
        final OffsetDateTime issued = parseTime(notamNode, FIELD_ISSUED);

        double latitude = 0.0;
        double longitude = 0.0;
        Double radiusNm = null;

        final JsonNode geometry = item.get(FIELD_GEOMETRY);  // use constant
        if (geometry != null) {
            JsonNode coords = null;
            if (geometry.has(FIELD_GEOMETRIES) && geometry.get(FIELD_GEOMETRIES).isArray()
                    && !geometry.get(FIELD_GEOMETRIES).isEmpty()) {
                coords = geometry.get(FIELD_GEOMETRIES).get(0).get(FIELD_COORDINATES);
            } else if (geometry.has(FIELD_COORDINATES)) {
                coords = geometry.get(FIELD_COORDINATES);
            }
            if (coords != null && coords.isArray()) {
                JsonNode pair = coords;
                while (pair.isArray() && !pair.isEmpty() && pair.get(0).isArray()) {
                    pair = pair.get(0);
                }
                if (pair.isArray() && pair.size() >= 2) {
                    longitude = pair.get(0).asDouble();
                    latitude = pair.get(1).asDouble();
                }
            }
        }

        final JsonNode r = notamNode.get(FIELD_RADIUS);
        if (r != null && !r.isNull()) {
            radiusNm = r.asDouble();
        }

        if (id == null || number == null || type == null || issued == null || icaoLocation == null || text == null) {
            logger.warn("Skipping NOTAM - missing required fields");
            return null;
        }

        return new Notam.Builder()
                .id(id)
                .number(number)
                .type(type)
                .issued(issued)
                .location(icaoLocation)
                .latitude(latitude)
                .longitude(longitude)
                .radiusNm(radiusNm)
                .text(text)
                .build();
    }

    private String parseText(final JsonNode parent, final String key) {
        final JsonNode n = parent.get(key);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private OffsetDateTime parseTime(final JsonNode parent, final String key) {
        final String textValue = parseText(parent, key);
        if (textValue == null) return null;
        try {
            return OffsetDateTime.parse(textValue);
        } catch (final Exception e) {
            logger.error("Failed to parse datetime from field '{}' with value '{}': {}",
                    key, textValue, e.getMessage());
            return null;
        }
    }
}