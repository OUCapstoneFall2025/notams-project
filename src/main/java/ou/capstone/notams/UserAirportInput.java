package ou.capstone.notams;

import java.io.Console;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserAirportInput {
    private static final Logger logger = LoggerFactory.getLogger(UserAirportInput.class);

    private UserAirportInput() { }

    /**
     * Collects exactly two airport codes from args or user prompt.
     * Includes placeholder for validation step.
     */
    public static List<String> getTwoAirportCodes(String[] args) {
        logger.info("Starting airport code collection");
        
        String from;
        String to;

        if (args != null && args.length >= 2) {
            logger.debug("Using command-line arguments for airport codes");
            from = args[0].trim().toUpperCase();
            to   = args[1].trim().toUpperCase();
            logger.debug("Airport codes from args - From: {}, To: {}", from, to);
        } else {
            logger.debug("Insufficient arguments, prompting user for airport codes");
            Console console = System.console();
            if (console == null) {
                logger.error("No console available and insufficient arguments provided");
                throw new IllegalStateException("No console available. Pass two airport codes as args.");
            }
            
            logger.debug("Prompting user for departure airport");
            from = console.readLine("Enter FROM airport (e.g., KOKC): ").trim().toUpperCase();
            logger.debug("Prompting user for destination airport");
            to   = console.readLine("Enter TO airport (e.g., KDFW): ").trim().toUpperCase();
            logger.debug("Airport codes from user input - From: {}, To: {}", from, to);
        }

        // Placeholder: validate codes once Jay's AirportCodeValidator is ready
        // AirportCodeValidator.validate(from);
        // AirportCodeValidator.validate(to);
        logger.debug("Airport code validation placeholder - validation will be implemented by Jay's AirportCodeValidator");

        logger.info("Airport code collection completed - From: {}, To: {}", from, to);
        return List.of(from, to);
    }
}