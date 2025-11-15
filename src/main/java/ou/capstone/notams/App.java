package ou.capstone.notams;

import ou.capstone.notams.api.NotamFetcher;
import ou.capstone.notams.api.NotamParser;
import ou.capstone.notams.validation.AirportValidator;
import ou.capstone.notams.validation.ValidationResult;
import ou.capstone.notams.prioritize.NotamPrioritizer;
import ou.capstone.notams.prioritize.SimplePrioritizer;
import ou.capstone.notams.print.NotamView;
import ou.capstone.notams.print.NotamPrinter;
import ou.capstone.notams.print.NotamPrinter.TimeMode;
import ou.capstone.notams.print.NotamColorPrinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Collections;

import java.time.ZoneId;
import java.time.Instant;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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

    private static ExitHandler exitHandler = new ExitHandler();

    public static void setExitHandler( final ExitHandler exitHandler )
    {
        App.exitHandler = exitHandler;
    }

    private App() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws ParseException
    {
        // Options could be marked as "required" but this interferes with the
        // ability to provide the help option (providing only `--help` means
        // that it will complain that --departure and --destination weren't
        // provided). Required args are manually checked below after handling
        // the help case.
        final Option departureAirportOption = Option.builder( "d" )
                .longOpt( "departure" ).hasArg()
                .desc( "Departure airport code" ).get();
        // 'a' for 'arrival' -- it's the best I could come up with that isn't
        // another d
        final Option destinationAirportOption = Option.builder( "a" )
                .longOpt( "destination" ).hasArg()
                .desc( "Destination airport code" ).get();
        final Option helpOption = Option.builder( "h" ).longOpt( "help" )
                .desc( "Display help" ).get();

        final Options options = new Options();
        options.addOption( departureAirportOption );
        options.addOption( destinationAirportOption );
        options.addOption( helpOption );

        final CommandLineParser cliParser = new DefaultParser();
        final CommandLine line;
        try {
            line = cliParser.parse( options, args );
        }
        catch( final ParseException e ) {
            logger.error( "Parsing args failed for reason: {}",
                    e.getMessage() );
            throw e;
        }

        if( line.hasOption( helpOption ) || line.getOptions().length == 0 ) {
            HelpFormatter helpFormatter = HelpFormatter.builder().get();
            helpFormatter.printHelp( "app",
                    "Notam Prioritization System Options", options,
                    "Please report bugs to https://ou-capstone-group-e.atlassian.net/jira/",
                    true );
            exitHandler.exit( 0 );
            return;
        }

        final boolean departureProvided = line.hasOption(
                departureAirportOption );
        final boolean destinationProvided = line.hasOption(
                destinationAirportOption );

        if( !departureProvided || !destinationProvided ) {
            throw new ParseException( "Invalid options: " + (departureProvided ?
                    "Destination" :
                    "Departure") + " airport code is required" );
        }

        logger.info( "NOTAM Prioritization System starting" );

        try {
            // Step 1: Get user input
            final String departureCode = line.getOptionValue(
                    departureAirportOption );
            final String destinationCode = line.getOptionValue(
                    destinationAirportOption );

            logger.info("Route: {} to {}", departureCode, destinationCode);

            // Step 2: Validate airports (delegated to AirportValidator)
            final AirportValidator validator = new AirportValidator();

            final ValidationResult departureResult = validator.validate(departureCode);
            if (!departureResult.isOk()) {
                logger.error("Invalid departure airport: {}", departureResult.message());
                System.err.println("Invalid departure airport: " + departureResult.message());
                exitHandler.exit( 1 );
                return;
            }

            final ValidationResult destinationResult = validator.validate(destinationCode);
            if (!destinationResult.isOk()) {
                logger.error("Invalid destination airport: {}", destinationResult.message());
                System.err.println("Invalid destination airport: " + destinationResult.message());
                exitHandler.exit( 1 );
                return;
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

            // Step 7: Display results
            displayResults(prioritizedNotams, departureCode, destinationCode);

            logger.info("NOTAM Prioritization System completed successfully");

        } catch (final IllegalStateException e) {
            logger.error("Configuration error: {}", e.getMessage());
            System.err.println("\nConfiguration Error: " + e.getMessage());
            System.err.println("Please ensure FAA_CLIENT_ID and FAA_CLIENT_SECRET environment variables are set.");
            exitHandler.exit(1);
            return;

        } catch (final IllegalArgumentException e) {
            logger.error("Invalid input: {}", e.getMessage());
            System.err.println("\nError: " + e.getMessage());
            exitHandler.exit(1);
            return;

        } catch (final Exception e) {
            logger.error("Unexpected error during execution", e);
            System.err.println("\nUnexpected Error: " + e.getMessage());
            exitHandler.exit(1);
            return;
        }
    }

    /**
     * Displays prioritized NOTAMs to the user.
     * Shows NOTAMs sorted by priority (most important first).
     *
     * @param prioritizedNotams list of NOTAMs already sorted by priority
     * @param departureCode departure airport code
     * @param destinationCode destination airport code
     */
    private static void displayResults(final List<Notam> prioritizedNotams,
                                       final String departureCode,
                                       final String destinationCode) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("NOTAMs for Flight: " + departureCode + " to " + destinationCode);
        System.out.println("Sorted by Priority (Most Important First)");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Map domain -> printer DTOs (score is left null)

        final List<NotamView> views = (prioritizedNotams == null) 
        		? Collections.emptyList()
                : prioritizedNotams.stream()
                    .map(n -> {
                        final Instant issued = (n.getIssued() != null) ? n.getIssued().toInstant() : null;
                        return new NotamView(
                                n.getNumber(),          // notamNumber
                                n.getLocation(),        // location (e.g., KOKC)
                                n.getType(),            // classification/type if present
                                issued,                 // start (best-effort)
                                issued,                 // end (best-effort)
                                n.getText(),            // condition text
                                null                    // score (SimplePrioritizer doesn’t mention one)
                        );
                    })
                    .collect(Collectors.toList());

        
        final NotamPrinter printer = new NotamColorPrinter(ZoneId.systemDefault(), TimeMode.BOTH);

        printer.print(views);
        System.out.println("\n" + "=".repeat(80) + "\n");
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

    public static class ExitHandler
    {
        public void exit( final int code )
        {
            System.exit( code );
        }
    }
}