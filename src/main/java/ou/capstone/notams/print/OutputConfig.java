package ou.capstone.notams.print;

/**
 * Configuration for NOTAM output formatting.
 * Controls how NOTAMs are displayed to the user.
 */
public record OutputConfig(
    /**
     * Output mode: "full" for complete NOTAM text, "truncated" for shortened text.
     */
    OutputMode outputMode,
    
    /**
     * Maximum length for truncated text (only used when outputMode is TRUNCATED).
     */
    int truncateLength,
    
    /**
     * Whether to show delimiters between NOTAMs.
     */
    boolean showDelimiters,
    
    /**
     * Whether to separate metadata (score, location, etc.) from NOTAM text.
     */
    boolean separateMetadata
) {
    public enum OutputMode {
        FULL,
        TRUNCATED
    }
    
    /**
     * Creates a default OutputConfig with full output, delimiters enabled, and metadata inline (not separated).
     */
    public static OutputConfig defaults() {
        return new OutputConfig(OutputMode.FULL, 100, true, false);
    }

    @Override
    public String toString() {
        return String.format("OutputConfig{outputMode=%s, truncateLength=%d, showDelimiters=%s, separateMetadata=%s}",
                outputMode, truncateLength, showDelimiters, separateMetadata);
    }
}

