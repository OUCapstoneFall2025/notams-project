package ou.capstone.notams.print;

public final class AnsiOutputHelper {

    private boolean enabled;

    public AnsiOutputHelper(final boolean enabled) {
        this.enabled = enabled;
    }

    public void enable()  { this.enabled = true;  }
    public void disable() { this.enabled = false; }

    public String colorRed(final String text)    { return apply(text, "\u001B[31m"); }
    public String colorYellow(final String text) { return apply(text, "\u001B[33m"); }
    public String colorBlue(final String text)   { return apply(text, "\u001B[34m"); }
    public String dim(final String text)         { return apply(text, "\u001B[2m");  }

    private String apply(final String text, final String code) {
        if (!enabled || text == null) return text;
        return code + text + "\u001B[0m";
    }
}