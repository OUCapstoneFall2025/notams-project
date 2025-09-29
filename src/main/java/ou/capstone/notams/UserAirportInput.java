package ou.capstone.notams;

import java.io.Console;
import java.util.List;

public final class UserAirportInput {

    private UserAirportInput() { }

    /**
     * Collects exactly two airport codes from args or user prompt.
     * Includes placeholder for validation step.
     */
    public static List<String> getTwoAirportCodes(String[] args) {
        String from;
        String to;

        if (args != null && args.length >= 2) {
            from = args[0].trim().toUpperCase();
            to   = args[1].trim().toUpperCase();
        } else {
            Console console = System.console();
            if (console == null) {
                throw new IllegalStateException("No console available. Pass two airport codes as args.");
            }
            from = console.readLine("Enter FROM airport (e.g., KOKC): ").trim().toUpperCase();
            to   = console.readLine("Enter TO airport (e.g., KDFW): ").trim().toUpperCase();
        }

        // Placeholder: validate codes once Jay's AirportCodeValidator is ready
        // AirportCodeValidator.validate(from);
        // AirportCodeValidator.validate(to);

        return List.of(from, to);
    }
}