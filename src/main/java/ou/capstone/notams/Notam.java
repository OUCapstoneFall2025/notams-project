package ou.capstone.notams;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents NOTAMs.
 * Includes identifiers, locations, timing, and text
 */
public class Notam {
    private final String id;           // FAA id
    private final String number;       // ex: "5/31"
    private final String type;         // ex: RUNWAY, TAXIWAY, AIRSPACE
    private final OffsetDateTime issued;
    private final String location;     // ICAO code (ex: KATL)
    private final Double latitude;     // decimal degrees
    private final Double longitude;    // decimal degrees
    private final Double radiusNm;     // radius in NM (nullable)
    private final String text;         // readable NOTAM text

    /**
     * Builder Pattern (Effective Java Item 2)
     * Provides clear and safe construction for classes with many parameters.
     * Required fields are validated; latitude/longitude and radiusNm are optional (nullable).
     */
    public static class Builder {
        private static final Logger logger = LoggerFactory.getLogger(Builder.class);

        // Required parameters
        private String id;           // FAA id
        private String number;       // ex: "5/31"
        private String type;         // ex: RUNWAY, TAXIWAY, AIRSPACE
        private OffsetDateTime issued;

        // Optional / nullable parameters
        private String location;     // ICAO code (ex: KATL)
        private Double latitude;     // decimal degrees (nullable)
        private Double longitude;    // decimal degrees (nullable)
        private Double radiusNm;     // radius in NM (nullable)
        private String text;         // readable NOTAM text

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder issued(OffsetDateTime issued) {
            this.issued = issued;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder radiusNm(Double radiusNm) {
            this.radiusNm = radiusNm;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Validates required parameters and constructs a Notam.
         * Latitude and longitude may be null for NOTAMs with no coordinate data.
         */
        public Notam build() {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(number, "number is required");
            Objects.requireNonNull(type, "type is required");
            Objects.requireNonNull(issued, "issued is required");
            Objects.requireNonNull(text, "text is required");

            if (latitude == null || longitude == null) {
                logger.debug("Building NOTAM {} with missing coordinates", id);
            }

            return new Notam(this);
        }
    }

    private Notam(Builder builder) {
        this.id = builder.id;
        this.number = builder.number;
        this.type = builder.type;
        this.issued = builder.issued;
        this.location = builder.location;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.radiusNm = builder.radiusNm;
        this.text = builder.text;
    }

    public String getId() { return id; }
    public String getNumber() { return number; }
    public String getType() { return type; }
    public OffsetDateTime getIssued() { return issued; }
    public String getLocation() { return location; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getRadiusNm() { return radiusNm; }
    public String getText() { return text; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notam)) return false;
        Notam notam = (Notam) o;
        return Objects.equals(id, notam.id) &&
               Objects.equals(number, notam.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, number);
    }

    @Override
    public String toString() {
        return "Notam{" +
                "id='" + id + '\'' +
                ", number='" + number + '\'' +
                ", type='" + type + '\'' +
                ", issued=" + issued +
                ", location='" + location + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", radiusNm=" + radiusNm +
                ", text='" + text + '\'' +
                '}';
    }
}