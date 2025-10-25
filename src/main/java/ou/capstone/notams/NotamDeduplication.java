package ou.capstone.notams;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class NotamDeduplication {

    private NotamDeduplication() {}

   
    public static List<Notam> dedup(final List<Notam> in) {
        if (in == null || in.isEmpty()) return in;

        final List<Notam> out = new ArrayList<>();
        final Map<String, Integer> keyToIndex = new LinkedHashMap<>();

        for (Notam n : in) {
            if (n == null) continue;
            final String key = keyFor(n);

            if (key == null) {
                out.add(n); 
                continue;
            }

            final Integer pos = keyToIndex.get(key);
            if (pos == null) {
                keyToIndex.put(key, out.size());
                out.add(n);
            } else {
                final Notam current = out.get(pos);
                if (prefer(n, current)) {
                    out.set(pos, n); // replace in order
                }
            }
        }
        return out;
    }

    private static String keyFor(final Notam n) {
        if (has(n.getId())) {
            return "ID|" + n.getId().trim();
        }

        final String num = nz(n.getNumber());
        final String loc = nz(n.getLocation());
        final OffsetDateTime issued = n.getIssued();

        if (!num.isEmpty() && !loc.isEmpty() && issued != null) {
            final String issuedMinute = issued.withSecond(0).withNano(0).toString();
            return "NLI|" + num + "|" + loc + "|" + issuedMinute;
        }

        return null;
    }

    /** Decide which NOTAM to keep when keys collide. */
    private static boolean prefer(final Notam candidate, final Notam current) {
        if (candidate.getIssued() != null && current.getIssued() != null) {
            int cmp = candidate.getIssued().compareTo(current.getIssued());
            if (cmp != 0) return cmp > 0;
        } else if (candidate.getIssued() != null) {
            return true;
        } else if (current.getIssued() != null) {
            return false;
        }

        final boolean candHasR = candidate.getRadiusNm() != null;
        final boolean currHasR = current.getRadiusNm() != null;
        if (candHasR != currHasR) return candHasR;

        final int candLen = length(candidate.getText());
        final int currLen = length(current.getText());
        if (candLen != currLen) return candLen > currLen;

        return false;
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String nz(String s)   { return s == null ? "" : s; }
    private static int length(String s)  { return s == null ? 0 : s.length(); }
}
