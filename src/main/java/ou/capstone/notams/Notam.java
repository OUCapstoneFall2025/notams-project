package ou.capstone.notams;


import java.time.OffsetDateTime;
import java.util.Objects;

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

    public Notam(
        final String id,
        final String number,
        final String type,
        final OffsetDateTime issued,
        final String location,
        final Double latitude,
        final Double longitude,
        final Double radiusNm,
        final String text
    ) {
        this.id = id;
        this.number = number;
        this.type = type;
        this.issued = issued;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusNm = radiusNm;
        this.text = text;
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

