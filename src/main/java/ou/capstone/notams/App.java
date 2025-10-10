package ou.capstone.notams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("NOTAMs CLI application starting");
        System.out.println("NOTAMs CLI ready.");
        System.out.println("Raw args: " + Arrays.toString(args));

        // Parse flags into a map: e.g., --dep JFK → dep=JFK
        logger.debug("Parsing command-line arguments");
        Map<String, String> parsed = parseArgs(args);
        logger.debug("Parsed arguments: {}", parsed);

        // Normalize airports: if 3 letters, add K
        logger.debug("Normalizing airport codes");
        String dep = normalizeAirport(parsed.get("dep"));
        String dest = normalizeAirport(parsed.get("dest"));

        logger.info("Flight route - Departure: {}, Destination: {}", dep, dest);
        System.out.println("Departure: " + dep);
        System.out.println("Destination: " + dest);
        
        logger.info("NOTAMs CLI application completed successfully");
    }

    private static Map<String, String> parseArgs(String[] args) {
        logger.debug("Starting argument parsing with {} arguments", args.length);
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2); // remove "--"
                String value = args[i + 1];
                map.put(key, value);
                logger.debug("Parsed argument: {} = {}", key, value);
            }
        }
        logger.debug("Argument parsing completed. Total parsed arguments: {}", map.size());
        return map;
    }

    private static String normalizeAirport(String code) {
        if (code == null) {
            logger.debug("Airport code is null, returning null");
            return null;
        }
        
        String originalCode = code;
        code = code.toUpperCase();
        logger.debug("Normalizing airport code: {} -> {}", originalCode, code);
        
        // If 3-letter FAA code, prefix with K
        if (code.length() == 3) {
            String normalized = "K" + code;
            logger.debug("Converted 3-letter FAA code {} to ICAO format: {}", code, normalized);
            return normalized;
        }
        
        logger.debug("Airport code {} is already in ICAO format", code);
        return code; // already ICAO
    }
}
