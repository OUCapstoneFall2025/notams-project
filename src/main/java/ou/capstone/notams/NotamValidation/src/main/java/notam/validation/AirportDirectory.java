package notam.validation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads airports from a CSV on the classpath.
 *
 * kept my CSV at: src/main/resources/data/us-airports.csv
 */
final class AirportDirectory {

    static final class Row {
        final String iata, icao, name, city, state;
        final double latitude, longitude;
        final Integer elevationFt;

        Row(String iata, String icao, String name, String city, String state,
            double latitude, double longitude, Integer elevationFt) {
            this.iata = iata == null ? "" : iata;
            this.icao = icao == null ? "" : icao;
            this.name = name == null ? "" : name;
            this.city = city == null ? "" : city;
            this.state = state == null ? "" : state;
            this.latitude = latitude;
            this.longitude = longitude;
            this.elevationFt = elevationFt;
        }
    }

    private final Map<String, Row> byIata = new HashMap<>();
    private final Map<String, Row> byIcao = new HashMap<>();
    private final Map<String, Row> byNameNormalized = new HashMap<>();
    private final List<Row> allRows = new ArrayList<>();

    AirportDirectory() {
        loadCsv("/data/us-airports.csv"); 
    }

    private void loadCsv(String resourcePath) {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalStateException("CSV not found on classpath: " + resourcePath +
                " (Is src/main/resources a Source Folder, and is the filename exact?)");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            // Read first non-blank header line
            String headerLine;
            do { headerLine = reader.readLine(); } while (headerLine != null && headerLine.isBlank());
            if (headerLine == null) throw new IOException("Empty CSV: " + resourcePath);

            // Strip BOM if present
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }

            List<String> header = parseCsvLine(headerLine);
            Map<String,Integer> idx = indexHeader(header);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < header.size()) {
                    while (cols.size() < header.size()) cols.add("");
                }

                String iata  = get(cols, idx, "IATA", "iata_code");
                String icao  = get(cols, idx, "ICAO", "ident");
                String name  = get(cols, idx, "Name", "name");
                String city  = get(cols, idx, "City", "municipality");
                String state = get(cols, idx, "State", "iso_region");
                String latS  = get(cols, idx, "Latitude", "latitude_deg");
                String lonS  = get(cols, idx, "Longitude", "longitude_deg");
                String elvS  = get(cols, idx, "Elevation_ft", "elevation_ft");

                // Normalize state if iso region like "US-CA"
                if (state != null && state.contains("-")) {
                    String[] parts = state.split("-", 2);
                    state = parts.length == 2 ? parts[1] : state;
                }

                if (name == null || name.isBlank()) continue;

                double lat = parseDoubleOrNaN(latS);
                double lon = parseDoubleOrNaN(lonS);
                Integer elev = parseIntegerOrNull(elvS);

                Row r = new Row(safe(iata), safe(icao), name, city, state, lat, lon, elev);

                allRows.add(r);
                if (!r.iata.isBlank()) byIata.put(r.iata.toUpperCase(Locale.ROOT), r);
                if (!r.icao.isBlank()) byIcao.put(r.icao.toUpperCase(Locale.ROOT), r);
                byNameNormalized.put(normalizeName(r.name), r);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load airport CSV: " + resourcePath, e);
        }
    }

    Optional<AirportId> findByIata(String iata) {
        Row r = byIata.get(iata.toUpperCase(Locale.ROOT));
        return r == null ? Optional.empty() : Optional.of(new AirportId(r.iata, AirportId.CodeType.IATA, r.name));
    }

    Optional<AirportId> findByIcao(String icao) {
        Row r = byIcao.get(icao.toUpperCase(Locale.ROOT));
        return r == null ? Optional.empty() : Optional.of(new AirportId(r.icao, AirportId.CodeType.ICAO, r.name));
    }

    Optional<AirportId> findByName(String name) {
        Row r = byNameNormalized.get(normalizeName(name));
        if (r == null) return Optional.empty();
        if (!r.iata.isBlank())
            return Optional.of(new AirportId(r.iata, AirportId.CodeType.IATA, r.name));
        if (!r.icao.isBlank())
            return Optional.of(new AirportId(r.icao, AirportId.CodeType.ICAO, r.name));
        return Optional.empty();
    }

    List<String> suggest(String input) {
        String n = normalizeName(input);
        List<String> out = new ArrayList<>(5);
        for (Row r : allRows) {
            if (normalizeName(r.name).contains(n)) {
                out.add(r.name + " — IATA " + (r.iata.isBlank() ? "—" : r.iata) + ", ICAO " + (r.icao.isBlank() ? "—" : r.icao));
                if (out.size() >= 5) break;
            }
        }
        return out;
    }

    // ---- helpers ----
    private static String normalizeName(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String get(List<String> cols, Map<String,Integer> idx, String primary, String fallback) {
        Integer i = idx.get(primary);
        if (i == null && fallback != null) i = idx.get(fallback);
        if (i == null) return null;
        return i < cols.size() ? cols.get(i).trim() : null;
    }

    private static Map<String,Integer> indexHeader(List<String> header) {
        Map<String,Integer> m = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i);
            if (h == null) continue;
            String key = h.trim();
            if (!key.isBlank()) m.put(key, i);
        }
        return m;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static double parseDoubleOrNaN(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static Integer parseIntegerOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    /** Minimal CSV parser (handles quotes, commas, and escaped quotes). */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        sb.append('\"'); i++; // escaped quote
                    } else {
                        inQuotes = false; // end quote
                    }
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
