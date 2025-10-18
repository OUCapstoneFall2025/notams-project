package ou.capstone.notams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;


public final class NotamScoringDemo {

    public static void main(final String[] args) {
        final Instant now = Instant.now();
        final Instant flightStart = now.plusSeconds(3600);       // flight in 1h
        final Instant flightEnd   = now.plusSeconds(3 * 3600);   // ends in 3h
        final Set<String> keyAirports = new HashSet<>(Arrays.asList("KJFK", "KLAX"));

        final List<ScoredNotam> notams = Arrays.asList(
            new ScoredNotam("N1",  "KJFK", "RUNWAY CLOSED RWY 13L/31R",               2.0,  now.plusSeconds(1800),    now.plusSeconds(7200),    now.minusSeconds(3600)),
            new ScoredNotam("N2",  "KJFK", "ILS OUT RWY 22R",                          8.0,  now.plusSeconds(2*3600),  now.plusSeconds(5*3600),  now.minusSeconds(30*3600)),
            new ScoredNotam("N3",  "KLAX", "TAXIWAY CLOSED TWY B",                     1.0,  now.plusSeconds(3000),    now.plusSeconds(9000),    now.minusSeconds(2*3600)),
            new ScoredNotam("N4",  "KSEA", "OBSTRUCTION CRANE 300FT AGL 12NM SW",     12.0,  now,                      now.plusSeconds(24*3600), now.minusSeconds(6*24*3600)),
            new ScoredNotam("N5",  "KDEN", "TFR ACTIVE WITHIN 5NM OF VORTAC",         18.0,  now.plusSeconds(4000),    now.plusSeconds(10*3600), now.minusSeconds(4*3600)),
            new ScoredNotam("N6",  "KDFW", "GENERAL ADVISORY: BIRD ACTIVITY",          6.0,  now.plusSeconds(86400),   now.plusSeconds(90000),   now.minusSeconds(10*24*3600)),
            new ScoredNotam("N7",  "KLAX", "GPS UNAVAIL INTERMITTENT",                 4.0,  now.plusSeconds(2000),    now.plusSeconds(6000),    now.minusSeconds(20*3600)),
            new ScoredNotam("N8",  "KPHX", "RUNWAY CLOSED RWY 08/26 OVERNIGHT",       22.0,  now.plusSeconds(8*3600),  now.plusSeconds(14*3600), now.minusSeconds(12*3600)),
            new ScoredNotam("N9",  "KORD", "TWY CLSD TWY K BETWEEN K3-K5",             3.0,  now.plusSeconds(10000),   now.plusSeconds(20000),   now.minusSeconds(2*3600)),
            new ScoredNotam("N10", "KBOS", "ADVISORY: WORK IN PROGRESS NE APRON",      2.0,  now.plusSeconds(20000),   now.plusSeconds(30000),   now.minusSeconds(50*3600))
        );

        final Scorer scorer = new Scorer();

        // Compute scores once and reuse
        final List<Result> results = new ArrayList<>(notams.size());
        for (final ScoredNotam n : notams) {
            final int s = scorer.score(n, flightStart, flightEnd, keyAirports);
            results.add(new Result(n, s));
        }

        // Random order 
        final List<Result> randomOrder = new ArrayList<>(results);
        Collections.shuffle(randomOrder, new Random());
        System.out.println("Random order:");
        printResults(randomOrder);

        // Sorted by score (desc) 
        final List<Result> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        System.out.println("\nSorted by score (desc):");
        printResults(sorted);

        // Basic sanity check to mimic an assertion (demo only)
        if (sorted.get(0).score < sorted.get(sorted.size() - 1).score) {
            throw new IllegalStateException("Sorting by score failed.");
        }
    }

    private static void printResults(final List<Result> results) {
        System.out.println("ID    Apt    Score  Text");
        System.out.println("----- ------ ------ --------------------------------------------------");
        for (final Result r : results) {
            final int rounded = roundToTens(r.score);
            System.out.println(toDisplayString(r.notam, rounded));
        }
    }

    private static String toDisplayString(final ScoredNotam n, final int score) {
        final String apt = (n.getAirport() == null) ? "-" : n.getAirport();
        final String rawText = (n.getText() == null) ? "" : n.getText();
        // Using string format pattern
        final String shortText = String.format(Locale.ROOT, "%.60s", rawText);
        return String.format(Locale.ROOT, "%-5s %-6s %6d %s", n.getId(), apt, score, shortText);
    }



    private static int roundToTens(final int x) {
        final int capped = Math.min(100, Math.max(0, x));
        final int r = ((capped + 5) / 10) * 10; // nearest 10
        return Math.min(100, r);
    }

    /** Holder for NOTAM + its score. */
    private static final class Result {
        final ScoredNotam notam;
        final int score;
        Result(final ScoredNotam notam, final int score) { this.notam = notam; this.score = score; }
    }

    /** Wrapper class that uses the official CCS-33 Notam class with additional scoring fields */
    static final class ScoredNotam {
        private final Notam baseNotam;  // Official CCS-33 Notam class
        private final Double distanceNm;  // Additional field for scoring
        private final Instant startTime;  // Additional field for scoring
        private final Instant endTime;    // Additional field for scoring
        private final Instant issuedTime; // Additional field for scoring

        ScoredNotam(final String id, final String airport, final String text, final Double distanceNm,
                    final Instant startTime, final Instant endTime, final Instant issuedTime) {
            // Create the official CCS-33 Notam object with available fields
            this.baseNotam = new Notam(
                id,
                "N/A", // number - not available in demo data
                "DEMO", // type - not available in demo data  
                issuedTime != null ? issuedTime.atOffset(java.time.ZoneOffset.UTC) : null,
                airport,
                0.0, // latitude - not available in demo data
                0.0, // longitude - not available in demo data
                distanceNm, // radiusNm - using distanceNm as radius
                text
            );
            this.distanceNm = distanceNm;
            this.startTime = startTime;
            this.endTime = endTime;
            this.issuedTime = issuedTime;
        }

        // Delegate to the official Notam class for basic properties
        public String getId() { return baseNotam.getId(); }
        public String getAirport() { return baseNotam.getLocation(); }
        public String getText() { return baseNotam.getText(); }
        public Double getDistanceNm() { return distanceNm; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Instant getIssuedTime() { return issuedTime; }
    }

    
    static final class Scorer {

        int score(final ScoredNotam notam,
                  final Instant flightStart,
                  final Instant flightEnd,
                  final Set<String> keyAirports) {
            final int total =
                    impact(notam.getText()) +
                    proximity(notam.getDistanceNm()) +
                    timeOverlap(notam.getStartTime(), notam.getEndTime(), flightStart, flightEnd) +
                    freshness(notam.getIssuedTime()) +
                    keyAirport(notam.getAirport(), keyAirports);
            return Math.min(100, Math.max(0, total));
        }

        private int impact(final String text) {
            if (text == null) return 0;
            final String t = text.toUpperCase(Locale.ROOT);
            if (containsAny(t, "RUNWAY CLOSED", "RWY CLSD", "RWY CLOSED")) return 60;
            if (containsAny(t, "TAXIWAY CLOSED", "TWY CLSD")) return 40;
            if (containsAny(t, "ILS", "GLIDESLOPE", "LOC OUT", "NAVAID OUT", "GPS UNAVAIL", "RAIM", "TFR", "TEMPORARY FLIGHT RESTRICTION"))
                return 40;
            if (containsAny(t, "OBSTRUCTION", "CRANE")) return 20;
            return 10; // advisory / other
        }

        private int proximity(final Double distanceNm) {
            if (distanceNm == null) return 0;
            final double d = Math.max(0, distanceNm);
            if (d <= 5)  return 20;
            if (d <= 20) return 10;
            return 0;
        }

        private int timeOverlap(final Instant nStart, final Instant nEnd,
                                final Instant fStart, final Instant fEnd) {
            if (nStart == null || nEnd == null || fStart == null || fEnd == null) return 0;
            final long s = Math.max(nStart.toEpochMilli(), fStart.toEpochMilli());
            final long e = Math.min(nEnd.toEpochMilli(),   fEnd.toEpochMilli());
            return (e > s) ? 10 : 0; // any overlap
        }

        private int freshness(final Instant issued) {
            if (issued == null) return 0;
            final long hours = Math.max(0, (Instant.now().toEpochMilli() - issued.toEpochMilli()) / (1000L * 60 * 60));
            return hours <= 24 ? 10 : 0;
        }

        private int keyAirport(final String airport, final Set<String> keyAirports) {
            if (airport == null || keyAirports == null) return 0;
            return keyAirports.contains(airport) ? 10 : 0;
        }

        private boolean containsAny(final String haystack, final String... needles) {
            for (final String n : needles) if (haystack.contains(n)) return true;
            return false;
        }
    }
}
