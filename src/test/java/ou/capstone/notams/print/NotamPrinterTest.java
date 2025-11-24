package ou.capstone.notams.print;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class NotamPrinterTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream buffer;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void printsEmptyState() {
        // Base printer, no color, default BOTH time mode
        final NotamPrinter printer = new NotamPrinter(ZoneId.of("UTC"));
        printer.print(List.of());
        final String out = buffer.toString();

        assertTrue(out.contains("No NOTAMs to display."),
                "Should print a helpful empty-state message.");
    }

    @Test
    void printsHeaderAndRows_withLocalTimesDisabled() {
        // Explicitly use UTC-only mode (no local times)
        final NotamPrinter printer =
                new NotamPrinter(ZoneId.of("UTC"), NotamPrinter.TimeMode.UTC_ONLY);

        final List<NotamView> views = List.of(
                new NotamView(
                        "10/068", "KOKC", "Aerodrome",
                        Instant.parse("2025-10-26T02:14:00Z"),
                        Instant.parse("2026-01-13T17:00:00Z"),
                        "AD AP WINDCONE FOR RWY 17R LGT U/S",
                        2.50
                ),
                new NotamView(
                        "10/633", "KDFW", "Aerodrome",
                        Instant.parse("2025-10-30T02:16:00Z"),
                        Instant.parse("2025-11-05T23:59:00Z"),
                        "AD AP WINDCONE FOR RWY 17R LGT U/S",
                        1.80
                )
        );

        printer.print(views);
        final String out = buffer.toString();

        // Header checks
        assertTrue(out.contains("Loc"), "Header should include Loc");
        assertTrue(out.contains("Start(UTC)"), "Header should include Start(UTC)");
        assertTrue(out.contains("End(UTC)"), "Header should include End(UTC)");

        // Row checks 
        assertTrue(out.contains("KOKC"), "Should include KOKC row");
        assertTrue(out.contains("10/068"), "Should include NOTAM number 10/068");
        assertTrue(out.contains("Aerodrome"), "Should include classification text");
        assertTrue(out.contains("2025-10-26 02:14Z"), "Should format UTC start time");

        // Ensure local times line is NOT printed
        assertTrue(!out.contains("Start(Local)"),
                "Local header should be absent when local times are disabled");
    }

    @Test
    void printsHeaderAndRows_withLocalTimesEnabled_andAnsiColorsEnabled() {
        // Use the color printer, which extends NotamPrinter and adds ANSI colors
        final NotamPrinter printer = new NotamColorPrinter(ZoneId.of("UTC"), NotamPrinter.TimeMode.BOTH);

        final List<NotamView> views = List.of(
                new NotamView(
                        "01/111", "KJFK", "Aerodrome",
                        Instant.parse("2025-01-02T10:00:00Z"),
                        Instant.parse("2025-02-02T10:00:00Z"),
                        "RUNWAY CLOSED RWY 13L/31R", // high-priority condition
                        120.0                         // high score → red in color printer
                )
        );

        printer.print(views);
        final String out = buffer.toString();

        // Header checks
        assertTrue(out.contains("Start(Local)"),
                "Local-times header should be present when BOTH time modes are enabled");

        // Row checks
        assertTrue(out.contains("KJFK"), "Should include the location KJFK");
        assertTrue(out.contains("01/111"), "Should include the NOTAM number 01/111");
        assertTrue(out.contains("RUNWAY CLOSED"), "Should include the condition text");

        // Local times line should appear under the row
        assertTrue(out.contains("2025-01-02 10:00 UTC"),
                "Local time line should include formatted local start (UTC zone here)");
    }
}
