package ou.capstone.notams;

/**
 * Configuration for NOTAM output formatting.
 * Supports configurable output options via command-line arguments.
 */
public final class OutputConfig {
    
    private final boolean fullOutput;
    private final int truncateLength;
    private final boolean showDelimiters;
    private final boolean separateMetadata;
    
    private static final int DEFAULT_TRUNCATE_LENGTH = 100;
    
    /**
     * Creates an OutputConfig with default settings.
     * Default: truncated output (100 chars), delimiters on, metadata separated.
     */
    public OutputConfig() {
        this.fullOutput = false;
        this.truncateLength = DEFAULT_TRUNCATE_LENGTH;
        this.showDelimiters = true;
        this.separateMetadata = true;
    }
    
    /**
     * Creates an OutputConfig with specified settings.
     * 
     * @param fullOutput if true, show full NOTAM text without truncation
     * @param truncateLength maximum text length when truncating (ignored if fullOutput is true)
     * @param showDelimiters if true, show delimiters between NOTAMs
     * @param separateMetadata if true, show metadata separately from text
     */
    public OutputConfig(final boolean fullOutput, final int truncateLength, 
                       final boolean showDelimiters, final boolean separateMetadata) {
        this.fullOutput = fullOutput;
        this.truncateLength = truncateLength > 0 ? truncateLength : DEFAULT_TRUNCATE_LENGTH;
        this.showDelimiters = showDelimiters;
        this.separateMetadata = separateMetadata;
    }
    
    public boolean isFullOutput() {
        return fullOutput;
    }
    
    public int getTruncateLength() {
        return truncateLength;
    }
    
    public boolean isShowDelimiters() {
        return showDelimiters;
    }
    
    public boolean isSeparateMetadata() {
        return separateMetadata;
    }
    
    /**
     * Parses command-line arguments to create an OutputConfig.
     * Expected format:
     *   --full-output : show full NOTAM text
     *   --truncate=N : truncate text to N characters (default: 100)
     *   --no-delimiters : disable delimiters between NOTAMs
     *   --inline-metadata : show metadata inline with text (default: separate)
     * 
     * @param args command-line arguments (after airport codes)
     * @return OutputConfig based on parsed arguments
     */
    public static OutputConfig parseArgs(final String[] args) {
        boolean fullOutput = false;
        int truncateLength = DEFAULT_TRUNCATE_LENGTH;
        boolean showDelimiters = true;
        boolean separateMetadata = true;
        
        if (args == null || args.length <= 2) {
            return new OutputConfig(fullOutput, truncateLength, showDelimiters, separateMetadata);
        }
        
        // Parse arguments starting from index 2 (after airport codes)
        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            
            if ("--full-output".equals(arg)) {
                fullOutput = true;
            } else if (arg.startsWith("--truncate=")) {
                try {
                    truncateLength = Integer.parseInt(arg.substring("--truncate=".length()));
                    if (truncateLength <= 0) {
                        truncateLength = DEFAULT_TRUNCATE_LENGTH;
                    }
                } catch (NumberFormatException e) {
                    // Invalid number, use default
                }
            } else if ("--no-delimiters".equals(arg)) {
                showDelimiters = false;
            } else if ("--inline-metadata".equals(arg)) {
                separateMetadata = false;
            }
        }
        
        return new OutputConfig(fullOutput, truncateLength, showDelimiters, separateMetadata);
    }
}

