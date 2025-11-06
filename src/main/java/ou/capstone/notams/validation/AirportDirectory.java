package ou.capstone.notams.validation;

import ou.capstone.notams.route.Coordinate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resource location: src/main/resources/data/us-airports.csv
 * Accessed via classpath (portable across OS/JARs): "/data/us-airports.csv"
 */
public class AirportDirectory {

    /**
     * Nullable fields allowed where data may be missing.
     */
    private record AirportRecord(String iataCode, String icaoCode, String localCode, String identifier, String name, String city,
                                 String state, double latitude, double longitude, Integer elevationFt) {
    }

    private static final String RESOURCE_PATH = "/data/us-airports.csv";

    // Indexes for fast lookups
    private final Map<String, AirportRecord> byIata = new HashMap<>();
    private final Map<String, AirportRecord> byIcao = new HashMap<>();
    private final Map<String, AirportRecord> byLocal = new HashMap<>();
    private final Map<String, AirportRecord> byNameNormalized = new HashMap<>();
    private final List<AirportRecord> allRows = new ArrayList<>();

    public AirportDirectory() {
        loadCsv();
    }

    private void loadCsv() {
        String resourcePath = RESOURCE_PATH;
        InputStream is = AirportDirectory.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalStateException(
                    "CSV not found on classpath: " + resourcePath +
                            " (Ensure src/main/resources is a Source Folder and file exists at data/us-airports.csv)"
            );
        }

        int skippedNoName = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String headerLine;
            do {
                headerLine = reader.readLine();
            } while (headerLine != null && headerLine.isBlank());
            if (headerLine == null) throw new IOException("Empty CSV: " + resourcePath);

            // Strip BOM if present
            if (headerLine.charAt(0) == '\uFEFF') headerLine = headerLine.substring(1);

            List<String> header = parseCsvLine(headerLine);
            Map<String, Integer> idx = indexHeader(header);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);

                final String iataCode = get(cols, idx, "IATA", "iata_code");
                final String icaoCode = get(cols, idx, "ICAO", "icao_code");
                final String localCode = get(cols, idx, "local_code");
                final String identifier = get(cols, idx, "ident");
                final String name = get(cols, idx, "Name", "name");
                final String city = get(cols, idx, "City", "municipality");
                String state = get(cols, idx, "State", "iso_region");
                final String latStr = get(cols, idx, "Latitude", "latitude_deg");
                final String lonStr = get(cols, idx, "Longitude", "longitude_deg");
                final String elevStr = get(cols, idx, "Elevation_ft", "elevation_ft");

                if (state != null && state.contains("-")) {
                    String[] parts = state.split("-", 2);
                    state = (parts.length == 2) ? parts[1] : state;
                }

                if (name == null || name.isBlank()) {
                    skippedNoName++;
                    continue;
                }

                final double lat = parseDoubleOrNaN(latStr);
                final double lon = parseDoubleOrNaN(lonStr);
                final Integer elev = parseIntegerOrNull(elevStr);

                AirportRecord rec = new AirportRecord(
                        blankToNull(iataCode),
                        blankToNull(icaoCode),
                        blankToNull(localCode),
                        blankToNull(identifier),
                        name,
                        blankToNull(city),
                        blankToNull(state),
                        lat, lon, elev
                );

                allRows.add(rec);
                if (rec.iataCode != null) byIata.put(rec.iataCode.toUpperCase(Locale.ROOT), rec);
                if (rec.icaoCode != null) byIcao.put(rec.icaoCode.toUpperCase(Locale.ROOT), rec);
                if (rec.localCode != null) byLocal.put(rec.localCode.toUpperCase(Locale.ROOT), rec);
                byNameNormalized.put(normalizeName(rec.name), rec);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load airport CSV: " + resourcePath, e);
        }

        if (skippedNoName > 0) {
            System.err.println("AirportDirectory: skipped " + skippedNoName + " rows with missing names.");
        }
    }

    Optional<AirportId> findByIata(String iata) {
        AirportRecord r = byIata.get(iata.toUpperCase(Locale.ROOT));
        return Optional.ofNullable(r).map(rec -> new AirportId(
                rec.iataCode != null ? rec.iataCode : rec.icaoCode,
                rec.iataCode != null ? AirportId.CodeType.IATA : AirportId.CodeType.ICAO,
                rec.name));
    }

    Optional<AirportId> findByIcao(String icao) {
        AirportRecord r = byIcao.get(icao.toUpperCase(Locale.ROOT));
        return Optional.ofNullable(r).map(rec -> new AirportId(
                rec.icaoCode != null ? rec.icaoCode : rec.iataCode,
                rec.icaoCode != null ? AirportId.CodeType.ICAO : AirportId.CodeType.IATA,
                rec.name));
    }

    public Optional<AirportId> findByLocal(String local) {
        AirportRecord r = byLocal.get(local.toUpperCase(Locale.ROOT));
        if (r == null) return Optional.empty();

        // Prefer ICAO > IATA > LOCAL
        if (r.icaoCode != null)
            return Optional.of(new AirportId(r.icaoCode, AirportId.CodeType.ICAO, r.name));
        if (r.iataCode != null)
            return Optional.of(new AirportId(r.iataCode, AirportId.CodeType.IATA, r.name));
        return Optional.of(new AirportId(r.localCode, AirportId.CodeType.LOCAL, r.name));
    }

    public Optional<AirportId> findByName(String name) {
        AirportRecord r = byNameNormalized.get(normalizeName(name));
        if (r == null) return Optional.empty();
        if (r.iataCode != null)
            return Optional.of(new AirportId(r.iataCode, AirportId.CodeType.IATA, r.name));
        if (r.icaoCode != null)
            return Optional.of(new AirportId(r.icaoCode, AirportId.CodeType.ICAO, r.name));
        if (r.localCode != null)
            return Optional.of(new AirportId(r.localCode, AirportId.CodeType.LOCAL, r.name));
        return Optional.empty();
    }

    List<String> suggest(String input) {
        final String needle = normalizeName(input);
        List<String> out = new ArrayList<>(5);
        for (AirportRecord r : allRows) {
            if (normalizeName(r.name).contains(needle)) {
                out.add(r.name + " — IATA " + (r.iataCode == null ? "—" : r.iataCode)
                        + ", ICAO " + (r.icaoCode == null ? "—" : r.icaoCode)
                        + ", Local " + (r.localCode == null ? "—" : r.localCode));
                if (out.size() >= 5) break;
            }
        }
        return out;
    }

    /**
     * Get airport coordinates by IATA, ICAO, or local code.
     * Tries all lookups automatically.
     */
    public Optional<Coordinate> getCoordinates(String code) {
        if (code == null || code.isBlank()) return Optional.empty();

        String upper = code.toUpperCase();
        AirportRecord r = byIcao.get(upper);

        // Try IATA if ICAO not found
        if (r == null) {
            r = byIata.get(upper);
        }

        // Try local code as last resort
        if (r == null) {
            r = byLocal.get(upper);
        }

        if (r == null) return Optional.empty();

        return Optional.of(new Coordinate(r.latitude, r.longitude));
    }

    private static String normalizeName(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String get(List<String> cols, Map<String, Integer> idx, String primary, String fallback) {
        Integer i = idx.get(primary);
        if (i == null && fallback != null) i = idx.get(fallback);
        if (i == null) return null;
        return (i < cols.size()) ? cols.get(i).trim() : null;
    }

    private static String get(List<String> cols, Map<String, Integer> idx, String columnName) {
        Integer i = idx.get(columnName);
        if (i == null) return null;
        return (i < cols.size()) ? cols.get(i).trim() : null;
    }

    private static Map<String, Integer> indexHeader(List<String> header) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i);
            if (h != null) {
                String key = h.trim();
                if (!key.isBlank()) m.put(key, i);
            }
        }
        return m;
    }

    private static double parseDoubleOrNaN(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Integer parseIntegerOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Minimal CSV parser (handles quotes, commas, and escaped quotes).
     */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        sb.append('\"');
                        i++;
                    } else inQuotes = false;
                } else {
                    sb.append(c);
                }
            } else {
                if (c == ',') {
                    out.add(sb.toString());
                    sb.setLength(0);
                } else if (c == '\"') {
                    inQuotes = true;
                } else {
                    sb.append(c);
                }
            }
        }
        out.add(sb.toString());
        return out;
    }
}