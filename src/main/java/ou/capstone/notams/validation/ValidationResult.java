package ou.capstone.notams.validation;

import java.util.List;
import java.util.Optional;

/**
 * Result of validating a user-provided airport identifier.
 */
public final class ValidationResult {
    private final boolean ok;
    private final String message;
    private final AirportId airport;
    private final List<String> suggestions;

    private ValidationResult(boolean ok, String message, AirportId airport, List<String> suggestions) {
        this.ok = ok;
        this.message = message;
        this.airport = airport;
        this.suggestions = (suggestions == null) ? List.of() : List.copyOf(suggestions);
    }

    public static ValidationResult success(AirportId id) {
        return new ValidationResult(true, "OK", id, List.of());
    }

    public static ValidationResult error(String message, List<String> suggestions) {
        return new ValidationResult(false, message, null, suggestions);
    }

    public boolean isOk() {
        return ok;
    }

    public String message() {
        return message;
    }

    public Optional<AirportId> airport() {
        return Optional.ofNullable(airport);
    }

    public List<String> suggestions() {
        return suggestions;
    }
}