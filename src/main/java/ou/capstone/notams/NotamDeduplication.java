package ou.capstone.notams;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Generates a deduplication key for a NOTAM.
     * Uses ID as primary key (most reliable).
     * Falls back to a composite key including number, location, type, and text content
     * to ensure distinct NOTAMs with same number but different content are preserved.
     */
    private static String keyFor(final Notam n) {
        // Primary: Use ID if available (most reliable identifier)
        if (has(n.getId())) {
            return "ID|" + n.getId().trim();
        }

        // Fallback: Use number + location + type + text content hash
        // This ensures NOTAMs with same number but different content (e.g., different runways)
        // are treated as distinct and preserved
        final String num = nz(n.getNumber());
        final String loc = nz(n.getLocation());
        final String type = nz(n.getType());
        final String text = nz(n.getText());

        if (!num.isEmpty() && !loc.isEmpty() && !type.isEmpty() && !text.isEmpty()) {
            final String normalizedText = normalizeText(text);
            final String textHash = hashText(normalizedText);
            return "NLT|" + num + "|" + loc + "|" + type + "|" + textHash;
        }

        // Last resort: Use number + location + issued time (less precise, may group distinct NOTAMs)
        final OffsetDateTime issued = n.getIssued();
        if (!num.isEmpty() && !loc.isEmpty() && issued != null) {
            final String issuedMinute = issued.withSecond(0).withNano(0).toString();
            return "NLI|" + num + "|" + loc + "|" + issuedMinute;
        }

        return null;
    }

    /**
     * Normalizes text for comparison: uppercase, collapse whitespace.
     * This helps identify truly identical NOTAMs even if formatting differs.
     */
    private static String normalizeText(final String text) {
        if (text == null || text.isEmpty()) return "";
        return text.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * Creates a short hash of the text content for use in deduplication keys.
     * Uses MD5 for speed (not security-critical).
     */
    private static String hashText(final String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final byte[] hashBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            // Use first 8 bytes as hex string (16 chars) for reasonable uniqueness
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, hashBytes.length); i++) {
                sb.append(String.format("%02x", hashBytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash code if MD5 unavailable
            return Integer.toHexString(text.hashCode());
        }
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
