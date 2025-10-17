package ou.capstone.notams.validation;

import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public final class AirportValidator {

    private final AirportDirectory directory = new AirportDirectory();

    private static final String IATA_RE = "^[A-Za-z]{3}$";
    private static final String ICAO_RE = "^[A-Za-z]{4}$";

    /** Validate user input and normalize to a canonical AirportId. */
    public ValidationResult validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return ValidationResult.error(
                "Airport not recognized. Please enter a valid IATA (3 letters), ICAO (4 letters), or common airport name.",
                List.of("Example IATA: LAX", "Example ICAO: KJFK", "Example Name: Los Angeles International Airport"));
        }

        final String input = raw.trim();

        // IATA
        if (input.matches(IATA_RE)) {
            return directory.findByIata(input)
                    .map(ValidationResult::success)
                    .orElseGet(() -> ValidationResult.error(
                        "Unknown IATA code: " + input.toUpperCase(Locale.ROOT),
                        directory.suggest(input)));
        }

        // ICAO
        if (input.matches(ICAO_RE)) {
            return directory.findByIcao(input)
                    .map(ValidationResult::success)
                    .orElseGet(() -> ValidationResult.error(
                        "Unknown ICAO code: " + input.toUpperCase(Locale.ROOT),
                        directory.suggest(input)));
        }

        // Name
        return directory.findByName(input)
                .map(ValidationResult::success)
                .orElseGet(() -> ValidationResult.error(
                    "Airport not recognized. Please use a standard IATA/ICAO code or a well-known airport name.",
                    directory.suggest(input)));
    }

    // CLI for manual testing 
    public static void main(String[] args) {
        AirportValidator validator = new AirportValidator();

        if (args.length > 0) {
            for (String arg : args) printResult(validator, arg);
            return;
        }

        System.out.println("Type an airport (IATA/ICAO/name). Press Enter on an empty line to quit.");
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null || line.isBlank()) break;
                printResult(validator, line);
            }
        }
    }

    private static void printResult(AirportValidator v, String s) {
        ValidationResult r = v.validate(s);
        if (r.isOk()) {
            System.out.println("✔ " + s + " → " + r.airport().get());
        } else {
            System.out.println("✖ " + s + " → " + r.message());
            if (!r.suggestions().isEmpty()) {
                System.out.println("   Suggestions:");
                for (String sug : r.suggestions()) System.out.println("    • " + sug);
            }
        }
    }
}
