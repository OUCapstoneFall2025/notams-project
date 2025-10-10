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
        final JsonNode properties = item.get("properties");
        if (properties == null) return null;

        final JsonNode coreData = properties.get("coreNOTAMData");
        if (coreData == null) return null;

        final JsonNode notamNode = coreData.get("notam");
        if (notamNode == null) return null;

        final String id = text(notamNode, "id");
        final String number = text(notamNode, "number");
        final String type = text(notamNode, "type");
        final String icaoLocation = text(notamNode, "icaoLocation");
        final String text = text(notamNode, "text");
        final OffsetDateTime issued = time(notamNode, "issued");

        double latitude = 0.0;
        double longitude = 0.0;
        Double radiusNm = null;

        final JsonNode geometry = item.get("geometry");
        if (geometry != null) {
            JsonNode coords = null;
            if (geometry.has("geometries") && geometry.get("geometries").isArray() && geometry.get("geometries").size() > 0) {
                coords = geometry.get("geometries").get(0).get("coordinates");
            } else if (geometry.has("coordinates")) {
                coords = geometry.get("coordinates");
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

        final JsonNode r = notamNode.get("radius");
        if (r != null && !r.isNull()) {
            radiusNm = r.asDouble();
        }

        if (id == null || number == null || type == null || issued == null || icaoLocation == null || text == null) {
            return null;
        }

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
    }

    private String text(final JsonNode parent, final String key) {
        final JsonNode n = parent.get(key);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private OffsetDateTime time(final JsonNode parent, final String key) {
        final JsonNode n = parent.get(key);
        if (n == null || n.isNull()) return null;
        try {
            return OffsetDateTime.parse(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}