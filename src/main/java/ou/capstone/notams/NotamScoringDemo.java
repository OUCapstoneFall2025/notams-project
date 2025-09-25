<<<<<<< HEAD:src/main/java/ou/capstone/notams/NotamScoringDemo.java
import java.time.Instant;
import java.util.*;

/**
 * NOTAM Scoring Demo (no Gradle/JUnit).
 */
public class NotamScoringDemo {

    
    static class Notam {
        final String id;
        final String airport;     
        final String text;        
        final Double distanceNm;  
        final Instant startTime;  
        final Instant endTime;    
        final Instant issuedTime; 

        Notam(String id, String airport, String text, Double distanceNm,
              Instant startTime, Instant endTime, Instant issuedTime) {
            this.id = id;
            this.airport = airport;
            this.text = text;
            this.distanceNm = distanceNm;
            this.startTime = startTime;
            this.endTime = endTime;
            this.issuedTime = issuedTime;
        }
    }

    // ---------- Scorer ----------
    static class Scorer {
        int score(Notam n, Instant flightStart, Instant flightEnd, Set<String> keyAirports) {
            int total =
                    impact(n.text) +
                    proximity(n.distanceNm) +
                    timeOverlap(n.startTime, n.endTime, flightStart, flightEnd) +
                    freshness(n.issuedTime) +
                    keyAirport(n.airport, keyAirports);

            return Math.min(100, total); 
        }

        int impact(String text) {
            if (text == null) return 0;
            String t = text.toUpperCase(Locale.ROOT);
            if (containsAny(t, "RUNWAY CLOSED", "RWY CLSD", "RWY CLOSED")) return 60;
            if (containsAny(t, "TAXIWAY CLOSED", "TWY CLSD")) return 40;
            if (containsAny(t, "ILS", "GLIDESLOPE", "LOC OUT", "NAVAID OUT", "GPS UNAVAIL", "RAIM", "TFR", "TEMPORARY FLIGHT RESTRICTION"))
                return 40;
            if (containsAny(t, "OBSTRUCTION", "CRANE")) return 20;
            return 10; 
        }

        int proximity(Double distanceNm) {
            if (distanceNm == null) return 0;
            double d = Math.max(0, distanceNm);
            if (d <= 5)  return 20;
            if (d <= 20) return 10;
            return 0;
        }

        int timeOverlap(Instant nStart, Instant nEnd, Instant fStart, Instant fEnd) {
            if (nStart == null || nEnd == null || fStart == null || fEnd == null) return 0;
            long s = Math.max(nStart.toEpochMilli(), fStart.toEpochMilli());
            long e = Math.min(nEnd.toEpochMilli(), fEnd.toEpochMilli());
            return (e > s) ? 10 : 0; 
        }

        int freshness(Instant issued) {
            if (issued == null) return 0;
            long hours = Math.max(0, (Instant.now().toEpochMilli() - issued.toEpochMilli()) / (1000L * 60 * 60));
            return hours <= 24 ? 10 : 0;
        }

        int keyAirport(String airport, Set<String> keyAirports) {
            if (airport == null || keyAirports == null) return 0;
            return keyAirports.contains(airport) ? 10 : 0;
        }

        boolean containsAny(String haystack, String... needles) {
            for (String n : needles) if (haystack.contains(n)) return true;
            return false;
        }
    }

    
    public static void main(String[] args) {
        Instant now = Instant.now();
        Instant flightStart = now.plusSeconds(3600);       // flight in 1h
        Instant flightEnd   = now.plusSeconds(3 * 3600);   // ends in 3h
        Set<String> keyAirports = new HashSet<>(Arrays.asList("KJFK", "KLAX"));

        List<Notam> list = Arrays.asList(
            new Notam("N1", "KJFK", "RUNWAY CLOSED RWY 13L/31R", 2.0,
                    now.plusSeconds(1800), now.plusSeconds(7200), now.minusSeconds(3600)),
            new Notam("N2", "KJFK", "ILS OUT RWY 22R", 8.0,
                    now.plusSeconds(2 * 3600), now.plusSeconds(5 * 3600), now.minusSeconds(30 * 3600)),
            new Notam("N3", "KLAX", "TAXIWAY CLOSED TWY B", 1.0,
                    now.plusSeconds(3000), now.plusSeconds(9000), now.minusSeconds(2 * 3600)),
            new Notam("N4", "KSEA", "OBSTRUCTION CRANE 300FT AGL 12NM SW", 12.0,
                    now, now.plusSeconds(24 * 3600), now.minusSeconds(6 * 24 * 3600)),
            new Notam("N5", "KDEN", "TFR ACTIVE WITHIN 5NM OF VORTAC", 18.0,
                    now.plusSeconds(4000), now.plusSeconds(10 * 3600), now.minusSeconds(4 * 3600)),
            new Notam("N6", "KDFW", "GENERAL ADVISORY: BIRD ACTIVITY", 6.0,
                    now.plusSeconds(86400), now.plusSeconds(90000), now.minusSeconds(10 * 24 * 3600)),
            new Notam("N7", "KLAX", "GPS UNAVAIL INTERMITTENT", 4.0,
                    now.plusSeconds(2000), now.plusSeconds(6000), now.minusSeconds(20 * 3600)),
            new Notam("N8", "KPHX", "RUNWAY CLOSED RWY 08/26 OVERNIGHT", 22.0,
                    now.plusSeconds(8 * 3600), now.plusSeconds(14 * 3600), now.minusSeconds(12 * 3600)),
            new Notam("N9", "KORD", "TWY CLSD TWY K BETWEEN K3-K5", 3.0,
                    now.plusSeconds(10000), now.plusSeconds(20000), now.minusSeconds(2 * 3600)),
            new Notam("N10","KBOS", "ADVISORY: WORK IN PROGRESS NE APRON", 2.0,
                    now.plusSeconds(20000), now.plusSeconds(30000), now.minusSeconds(50 * 3600))
        );

        Scorer scorer = new Scorer();

        List<Notam> randomOrder = new ArrayList<>(list);
        Collections.shuffle(randomOrder, new Random()); 
        System.out.println("Random order:");
        printWithScores(randomOrder, scorer, flightStart, flightEnd, keyAirports);

        // Sort by score 
        List<Notam> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(
                scorer.score(b, flightStart, flightEnd, keyAirports),
                scorer.score(a, flightStart, flightEnd, keyAirports)
        ));
        System.out.println("\nSorted by score (desc):");
        printWithScores(sorted, scorer, flightStart, flightEnd, keyAirports);

        // Simple check
        int top = scorer.score(sorted.get(0), flightStart, flightEnd, keyAirports);
        int bottom = scorer.score(sorted.get(sorted.size()-1), flightStart, flightEnd, keyAirports);
        if (top < bottom) throw new AssertionError("Sorting by score failed.");
    }

    
    static void printWithScores(List<Notam> items, Scorer scorer, Instant fs, Instant fe, Set<String> keys) {
        System.out.println("ID    Apt    Score  Text");
        System.out.println("----- ------ ------ --------------------------------------------------");
        for (Notam n : items) {
            int raw = scorer.score(n, fs, fe, keys);
            int rounded = roundToTens(raw);
            System.out.printf("%-5s %-6s %6d %s%n",
                    n.id, n.airport == null ? "-" : n.airport, rounded, truncate(n.text, 60));
        }
    }

    static int roundToTens(int x) {
        int capped = Math.min(100, Math.max(0, x));
        int r = ((capped + 5) / 10) * 10; 
        return Math.min(100, r);
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
=======
import java.time.Instant;
import java.util.*;

/** NOTAM Scoring Demo (no Gradle/JUnit). */
public class NotamScoringDemo {

    public static void main(final String[] args) {
        final Instant now = Instant.now();
        final Instant flightStart = now.plusSeconds(3600);       // flight in 1h
        final Instant flightEnd   = now.plusSeconds(3 * 3600);   // ends in 3h
        final Set<String> keyAirports = new HashSet<>(Arrays.asList("KJFK", "KLAX"));

        final List<Notam> notams = Arrays.asList(
            new Notam("N1",  "KJFK", "RUNWAY CLOSED RWY 13L/31R",              2.0,  now.plusSeconds(1800),   now.plusSeconds(7200),   now.minusSeconds(3600)),
            new Notam("N2",  "KJFK", "ILS OUT RWY 22R",                         8.0,  now.plusSeconds(2*3600), now.plusSeconds(5*3600), now.minusSeconds(30*3600)),
            new Notam("N3",  "KLAX", "TAXIWAY CLOSED TWY B",                    1.0,  now.plusSeconds(3000),   now.plusSeconds(9000),   now.minusSeconds(2*3600)),
            new Notam("N4",  "KSEA", "OBSTRUCTION CRANE 300FT AGL 12NM SW",    12.0,  now,                     now.plusSeconds(24*3600),now.minusSeconds(6*24*3600)),
            new Notam("N5",  "KDEN", "TFR ACTIVE WITHIN 5NM OF VORTAC",        18.0,  now.plusSeconds(4000),   now.plusSeconds(10*3600),now.minusSeconds(4*3600)),
            new Notam("N6",  "KDFW", "GENERAL ADVISORY: BIRD ACTIVITY",         6.0,  now.plusSeconds(86400),  now.plusSeconds(90000),  now.minusSeconds(10*24*3600)),
            new Notam("N7",  "KLAX", "GPS UNAVAIL INTERMITTENT",                4.0,  now.plusSeconds(2000),   now.plusSeconds(6000),   now.minusSeconds(20*3600)),
            new Notam("N8",  "KPHX", "RUNWAY CLOSED RWY 08/26 OVERNIGHT",      22.0,  now.plusSeconds(8*3600), now.plusSeconds(14*3600),now.minusSeconds(12*3600)),
            new Notam("N9",  "KORD", "TWY CLSD TWY K BETWEEN K3-K5",            3.0,  now.plusSeconds(10000),  now.plusSeconds(20000),  now.minusSeconds(2*3600)),
            new Notam("N10", "KBOS", "ADVISORY: WORK IN PROGRESS NE APRON",     2.0,  now.plusSeconds(20000),  now.plusSeconds(30000),  now.minusSeconds(50*3600))
        );

        final Scorer scorer = new Scorer();

        // Compute scores only once
        final List<Result> results = new ArrayList<>(notams.size());
        for (final Notam n : notams) {
            final int s = scorer.score(n, flightStart, flightEnd, keyAirports);
            results.add(new Result(n, s));
        }

        // 1) Random order 
        final List<Result> randomOrder = new ArrayList<>(results);
        Collections.shuffle(randomOrder, new Random());
        System.out.println("Random order:");
        printResults(randomOrder);

        // 2) Sorted by score order (desc)
        final List<Result> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        System.out.println("\nSorted by score (desc):");
        printResults(sorted);

        // Simple check 
        final int topScore = sorted.get(0).score;
        final int bottomScore = sorted.get(sorted.size() - 1).score;
        if (topScore < bottomScore) throw new AssertionError("Sorting by score failed.");
    }

    private static void printResults(final List<Result> results) {
        System.out.println("ID    Apt    Score  Text");
        System.out.println("----- ------ ------ --------------------------------------------------");
        for (final Result r : results) {
            final int rounded = roundToTens(r.score);
            System.out.println(r.notam.toDisplayString(rounded));
        }
    }

    private static int roundToTens(final int x) {
        final int capped = Math.min(100, Math.max(0, x));
        final int r = ((capped + 5) / 10) * 10; // nearest 10
        return Math.min(100, r);
    }

    /** Holder for NOTAM + its precomputed score. */
    static final class Result {
        final Notam notam;
        final int score;
        Result(final Notam notam, final int score) { this.notam = notam; this.score = score; }
    }
}

/** Temp NOTAM class for this demo. Replace with CCS-33 Notam class later. */
class Notam {
    final String id;
    final String airport;     
    final String text;        
    final Double distanceNm;  
    final Instant startTime;  
    final Instant endTime;    
    final Instant issuedTime; 

    Notam(final String id, final String airport, final String text, final Double distanceNm,
          final Instant startTime, final Instant endTime, final Instant issuedTime) {
        this.id = id;
        this.airport = airport;
        this.text = text;
        this.distanceNm = distanceNm;
        this.startTime = startTime;
        this.endTime = endTime;
        this.issuedTime = issuedTime;
    }

   
    public String toDisplayString(final int score) {
        final String apt = (airport == null) ? "-" : airport;
        final String shortText = truncate(text, 60);
        return String.format("%-5s %-6s %6d %s", id, apt, score, shortText);
    }

    private static String truncate(final String s, final int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}

/** Rule-based scorer. Public score; helpers private. */
class Scorer {

    public int score(final Notam notam,
                     final Instant flightStart,
                     final Instant flightEnd,
                     final Set<String> keyAirports) {
        final int total =
                impact(notam.text) +
                proximity(notam.distanceNm) +
                timeOverlap(notam.startTime, notam.endTime, flightStart, flightEnd) +
                freshness(notam.issuedTime) +
                keyAirport(notam.airport, keyAirports);
        return Math.min(100, total);
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

    private int timeOverlap(final Instant notamStart, final Instant notamEnd,
                            final Instant flightStart, final Instant flightEnd) {
        if (notamStart == null || notamEnd == null || flightStart == null || flightEnd == null) return 0;
        final long s = Math.max(notamStart.toEpochMilli(), flightStart.toEpochMilli());
        final long e = Math.min(notamEnd.toEpochMilli(),   flightEnd.toEpochMilli());
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
>>>>>>> 8e1c04f (CCS-13: Changes in NotamScoringDemo.java):tempNotams/NotamScoringDemo.java
