package ou.capstone.notams.validation;

public final class AirportId {
    public enum CodeType { IATA, ICAO }

    private final String code;
    private final CodeType type;
    private final String displayName;

    public AirportId(String code, CodeType type, String displayName) {
        this.code = code;
        this.type = type;
        this.displayName = displayName;
    }

    public String code() { return code; }
    public CodeType type() { return type; }
    public String displayName() { return displayName; }

    @Override
    public String toString() {
        return type + ":" + code + " (" + displayName + ")";
    }
}
