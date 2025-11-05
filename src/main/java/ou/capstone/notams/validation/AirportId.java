package ou.capstone.notams.validation;

public record AirportId(String code, CodeType type, String displayName) {
    public enum CodeType {IATA, ICAO, LOCAL}

    @Override
    public String toString() {
        return type + ":" + code + " (" + displayName + ")";
    }
}