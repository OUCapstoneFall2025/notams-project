package ou.capstone.notams;

import ou.capstone.notams.api.NotamFetcher;
import ou.capstone.notams.api.NotamParser;
import ou.capstone.notams.validation.AirportValidator;
import ou.capstone.notams.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main application driver for the NOTAM Prioritization System.
 *
 * Orchestrates the flow between components:
 * - User input collection and validation
 * - NOTAM fetching via FAA API
 * - GeoJSON parsing
 * - Scoring via NotamScoringDemo.Scorer
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

            logger.info("Airports validated");

            // Step 3: Fetch NOTAMs (delegated to NotamFetcher)
            final NotamFetcher fetcher = new NotamFetcher();
            final List<String> apiResponses = fetcher.fetchForRoute(departureCode, destinationCode);

            logger.info("Fetched {} API responses", apiResponses.size());

            // Step 4: Parse NOTAMs (delegated to NotamParser)
            final NotamParser parser = new NotamParser();
            final List<Notam> allNotams = new ArrayList<>();

            for (final String response : apiResponses) {
                final List<Notam> parsedNotams = parser.parseGeoJson(response);
                allNotams.addAll(parsedNotams);
            }

            logger.info("Parsed {} NOTAMs", allNotams.size());

            // Step 5: Score NOTAMs (delegated to NotamScoringDemo.Scorer)
            final Set<String> keyAirports = new HashSet<>(Arrays.asList(departureCode, destinationCode));
            final Instant flightStart = Instant.now().plusSeconds(3600); // Assume flight in 1 hour
            final Instant flightEnd = flightStart.plusSeconds(2 * 3600);  // Assume 2 hour flight

            final NotamScoringDemo.Scorer scorer = new NotamScoringDemo.Scorer();
            final List<ScoredNotam> scoredNotams = new ArrayList<>();

            for (final Notam notam : allNotams) {
                final NotamScoringDemo.ScoredNotam wrappedNotam = wrapNotam(notam);
                final int score = scorer.score(wrappedNotam, flightStart, flightEnd, keyAirports);
                scoredNotams.add(new ScoredNotam(notam, score));
            }

            // Sort by score (highest first)
            scoredNotams.sort((a, b) -> Integer.compare(b.score, a.score));

            logger.info("Scored {} NOTAMs", scoredNotams.size());

            // Step 6: Display results
            displayResults(scoredNotams, departureCode, destinationCode);

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
     * Wraps a Notam in NotamScoringDemo.ScoredNotam for scoring.
     * Maps available fields from real Notam to ScoredNotam format.
     *
     * @param notam the Notam to wrap
     * @return wrapped ScoredNotam compatible with Scorer
     */
    private static NotamScoringDemo.ScoredNotam wrapNotam(final Notam notam) {
        final Instant issuedTime = notam.getIssued() != null ? notam.getIssued().toInstant() : null;

        // ScoredNotam expects startTime and endTime which we don't have in parsed NOTAMs
        // Use issued time as a proxy for now
        return new NotamScoringDemo.ScoredNotam(
                notam.getId(),
                notam.getLocation(),
                notam.getText(),
                notam.getRadiusNm(),
                issuedTime, // startTime - not available, using issued
                issuedTime, // endTime - not available, using issued
                issuedTime
        );
    }

    /**
     * Displays scored NOTAMs to the user.
     * Shows NOTAMs sorted by usefulness score.
     *
     * @param scoredNotams list of NOTAMs with scores (sorted by score descending)
     * @param departureCode departure airport code
     * @param destinationCode destination airport code
     */
    private static void displayResults(final List<ScoredNotam> scoredNotams,
                                       final String departureCode,
                                       final String destinationCode) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("NOTAMs for Flight: " + departureCode + " to " + destinationCode);
        System.out.println("Sorted by Usefulness (Most Useful First)");
        System.out.println("=".repeat(80));

        if (scoredNotams.isEmpty()) {
            System.out.println("\nNo NOTAMs found for this route.");
            return;
        }

        System.out.println("\nTotal NOTAMs: " + scoredNotams.size());
        System.out.println("\nScore: 0-100 (higher = more useful)\n");

        System.out.println(String.format(Locale.ROOT, "%-6s %-8s %-6s %-10s %s",
                "Score", "Number", "Loc", "Issued", "Text"));
        System.out.println("-".repeat(80));

        for (final ScoredNotam sn : scoredNotams) {
            final Notam notam = sn.notam;
            final int roundedScore = roundToTens(sn.score);

            final String location = notam.getLocation() != null ? notam.getLocation() : "N/A";
            final String issued = notam.getIssued() != null ? notam.getIssued().toString().substring(0, 10) : "N/A";
            final String text = notam.getText() != null ? truncate(notam.getText(), 50) : "";

            System.out.println(String.format(Locale.ROOT, "%-6d %-8s %-6s %-10s %s",
                    roundedScore,
                    notam.getNumber(),
                    location,
                    issued,
                    text));
        }

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    /**
     * Rounds score to nearest 10.
     * Delegated from NotamScoringDemo display logic.
     *
     * @param score raw score
     * @return rounded score
     */
    private static int roundToTens(final int score) {
        final int capped = Math.min(100, Math.max(0, score));
        final int rounded = ((capped + 5) / 10) * 10;
        return Math.min(100, rounded);
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
     * Simple container for a Notam with its score.
     */
    private static final class ScoredNotam {
        final Notam notam;
        final int score;

        ScoredNotam(final Notam notam, final int score) {
            this.notam = notam;
            this.score = score;
        }
    }
}