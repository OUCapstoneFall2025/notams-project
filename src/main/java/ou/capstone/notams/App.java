package ou.capstone.notams;

import ou.capstone.notams.NotamDeduplication;
import ou.capstone.notams.api.NotamFetcher;
import ou.capstone.notams.api.NotamParser;
import ou.capstone.notams.validation.AirportValidator;
import ou.capstone.notams.validation.ValidationResult;
import ou.capstone.notams.prioritize.NotamPrioritizer;
import ou.capstone.notams.prioritize.SimplePrioritizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main application driver for the NOTAM Prioritization System.
 *
 * Orchestrates the flow between components:
 * - User input collection and validation
 * - NOTAM fetching via FAA API
 * - GeoJSON parsing
 * - Prioritization via SimplePrioritizer
 * - Display of results
 */
public final class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private App() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        logger.info("NOTAM Prioritization System starting");

        try {
            // Step 1: Get user input (delegated to UserAirportInput)
            final List<String> airportCodes = UserAirportInput.getTwoAirportCodes(args);
            final String departureCode = airportCodes.get(0);
            final String destinationCode = airportCodes.get(1);

            logger.info("Route: {} to {}", departureCode, destinationCode);

            // Step 2: Validate airports (delegated to AirportValidator)
            final AirportValidator validator = new AirportValidator();

            final ValidationResult departureResult = validator.validate(departureCode);
            if (!departureResult.isOk()) {
                logger.error("Invalid departure airport: {}", departureResult.message());
                System.err.println("Invalid departure airport: " + departureResult.message());
                System.exit(1);
            }

            final ValidationResult destinationResult = validator.validate(destinationCode);
            if (!destinationResult.isOk()) {
                logger.error("Invalid destination airport: {}", destinationResult.message());
                System.err.println("Invalid destination airport: " + destinationResult.message());
                System.exit(1);
            }

            // Step 3: Use validated airports to fetch NOTAMs
            logger.info("Airports validated");
            final String validatedDepartureCode = getCodeFromValidation(departureResult);
            final String validatedDestinationCode = getCodeFromValidation(destinationResult);

            logger.info("Using validated codes for API: {} to {}", validatedDepartureCode, validatedDestinationCode);
            final NotamFetcher fetcher = new NotamFetcher();
            final List<String> apiResponses = fetcher.fetchForRoute(validatedDepartureCode, validatedDestinationCode);

            logger.info("Fetched {} API responses", apiResponses.size());

            // Step 4: Parse NOTAMs (delegated to NotamParser)
            final NotamParser parser = new NotamParser();
            final List<Notam> allNotams = new ArrayList<>();

            for (final String response : apiResponses) {
                final List<Notam> parsedNotams = parser.parseGeoJson(response);
                allNotams.addAll(parsedNotams);
            }

            logger.info("Parsed {} NOTAMs", allNotams.size());
            
            // Step 5: Deduplication
            final List<Notam> uniqueNotams = NotamDeduplication.dedup(allNotams);
            logger.info("Dedup result: {} → {} unique NOTAMs",
                    allNotams.size(), uniqueNotams.size());
         
            // Step 6: Prioritize NOTAMs (delegated to SimplePrioritizer)
            final NotamPrioritizer prioritizer = new SimplePrioritizer();
            final List<Notam> prioritizedNotams = prioritizer.prioritize(uniqueNotams);

            logger.info("Prioritized {} NOTAMs", prioritizedNotams.size());

            // Step 7: Parse output configuration from command-line arguments
            final OutputConfig outputConfig = OutputConfig.parseArgs(args);
            logger.debug("Output config: fullOutput={}, truncateLength={}, showDelimiters={}, separateMetadata={}",
                    outputConfig.isFullOutput(), outputConfig.getTruncateLength(),
                    outputConfig.isShowDelimiters(), outputConfig.isSeparateMetadata());

            // Step 8: Display results
            displayResults(prioritizedNotams, departureCode, destinationCode, outputConfig);

            logger.info("NOTAM Prioritization System completed successfully");

        } catch (final IllegalStateException e) {
            logger.error("Configuration error: {}", e.getMessage());
            System.err.println("\nConfiguration Error: " + e.getMessage());
            System.err.println("Please ensure FAA_CLIENT_ID and FAA_CLIENT_SECRET environment variables are set.");
            System.exit(1);

        } catch (final IllegalArgumentException e) {
            logger.error("Invalid input: {}", e.getMessage());
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);

        } catch (final Exception e) {
            logger.error("Unexpected error during execution", e);
            System.err.println("\nUnexpected Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Displays prioritized NOTAMs to the user.
     * Shows NOTAMs sorted by priority (most important first).
     * Output format is configurable via OutputConfig.
     *
     * @param prioritizedNotams list of NOTAMs already sorted by priority
     * @param departureCode departure airport code
     * @param destinationCode destination airport code
     * @param outputConfig configuration for output formatting
     */
    private static void displayResults(final List<Notam> prioritizedNotams,
                                       final String departureCode,
                                       final String destinationCode,
                                       final OutputConfig outputConfig) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("NOTAMs for Flight: " + departureCode + " to " + destinationCode);
        System.out.println("Sorted by Priority (Most Important First)");
        System.out.println("=".repeat(80));

        if (prioritizedNotams.isEmpty()) {
            System.out.println("\nNo NOTAMs found for this route.");
            return;
        }

        System.out.println("\nTotal NOTAMs: " + prioritizedNotams.size());
        System.out.println();

        if (outputConfig.isSeparateMetadata()) {
            displayWithSeparateMetadata(prioritizedNotams, outputConfig);
        } else {
            displayWithInlineMetadata(prioritizedNotams, outputConfig);
        }

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    /**
     * Displays NOTAMs with metadata separated from text.
     * Shows metadata in a table, then full/truncated text below.
     */
    private static void displayWithSeparateMetadata(final List<Notam> prioritizedNotams,
                                                    final OutputConfig outputConfig) {
        // Display metadata table
        System.out.println(String.format(Locale.ROOT, "%-5s %-8s %-6s %-6s %-10s",
                "Rank", "Number", "Type", "Loc", "Issued"));
        System.out.println("-".repeat(80));

        int rank = 1;
        for (final Notam notam : prioritizedNotams) {
            final String type = notam.getType() != null ? truncate(notam.getType(), 6) : "N/A";
            final String location = notam.getLocation() != null ? notam.getLocation() : "N/A";
            final String issued = notam.getIssued() != null ? notam.getIssued().toString().substring(0, 10) : "N/A";

            System.out.println(String.format(Locale.ROOT, "%-5d %-8s %-6s %-6s %-10s",
                    rank++,
                    notam.getNumber(),
                    type,
                    location,
                    issued));
        }

        // Display text separately
        System.out.println("\n" + "-".repeat(80));
        System.out.println("NOTAM Text:");
        System.out.println("-".repeat(80));

        rank = 1;
        for (final Notam notam : prioritizedNotams) {
            if (outputConfig.isShowDelimiters() && rank > 1) {
                System.out.println("\n" + "-".repeat(80));
            }

            final String text = notam.getText() != null ? notam.getText() : "";
            final String displayText = outputConfig.isFullOutput() 
                    ? text 
                    : truncate(text, outputConfig.getTruncateLength());

            System.out.println(String.format(Locale.ROOT, "[Rank %d] %s", rank++, displayText));
        }
    }

    /**
     * Displays NOTAMs with metadata inline with text (original format).
     */
    private static void displayWithInlineMetadata(final List<Notam> prioritizedNotams,
                                                  final OutputConfig outputConfig) {
        System.out.println(String.format(Locale.ROOT, "%-5s %-8s %-6s %-6s %-10s %s",
                "Rank", "Number", "Type", "Loc", "Issued", "Text"));
        System.out.println("-".repeat(80));

        int rank = 1;
        for (final Notam notam : prioritizedNotams) {
            if (outputConfig.isShowDelimiters() && rank > 1) {
                System.out.println("-".repeat(80));
            }

            final String type = notam.getType() != null ? truncate(notam.getType(), 6) : "N/A";
            final String location = notam.getLocation() != null ? notam.getLocation() : "N/A";
            final String issued = notam.getIssued() != null ? notam.getIssued().toString().substring(0, 10) : "N/A";
            final String text = notam.getText() != null ? notam.getText() : "";
            final String displayText = outputConfig.isFullOutput() 
                    ? text 
                    : truncate(text, outputConfig.getTruncateLength());

            System.out.println(String.format(Locale.ROOT, "%-5d %-8s %-6s %-6s %-10s %s",
                    rank++,
                    notam.getNumber(),
                    type,
                    location,
                    issued,
                    displayText));
        }
    }

    /**
     * Truncates text to specified length.
     *
     * @param text text to truncate
     * @param maxLength maximum length
     * @return truncated text
     */
    private static String truncate(final String text, final int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Extracts the airport code from a ValidationResult.
     * If the result contains an ICAO code, returns it.
     * If the result contains an IATA code, returns it (may need conversion for API).
     *
     * @param result the validation result
     * @return the airport code
     */
    private static String getCodeFromValidation(final ValidationResult result) {
        if (!result.isOk() || !result.airport().isPresent()) {
            throw new IllegalStateException("Cannot extract code from invalid validation result");
        }
        return result.airport().get().code();
    }
}