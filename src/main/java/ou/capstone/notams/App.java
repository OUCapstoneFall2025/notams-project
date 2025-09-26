package ou.capstone.notams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        System.out.println("NOTAMs CLI ready.");
        System.out.println("Raw args: " + Arrays.toString(args));

        // Parse flags into a map: e.g., --dep JFK → dep=JFK
        Map<String, String> parsed = parseArgs(args);

        // Normalize airports: if 3 letters, add K
        String dep = normalizeAirport(parsed.get("dep"));
        String dest = normalizeAirport(parsed.get("dest"));

        System.out.println("Departure: " + dep);
        System.out.println("Destination: " + dest);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2); // remove "--"
                map.put(key, args[i + 1]);
            }
        }
        return map;
    }

    private static String normalizeAirport(String code) {
        if (code == null) return null;
        code = code.toUpperCase();
        // If 3-letter FAA code, prefix with K
        if (code.length() == 3) {
            return "K" + code;
        }
        return code; // already ICAO
    }
}
