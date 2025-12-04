package ou.capstone.notams.exceptions;

public class RateLimitException extends Exception {
    public RateLimitException(String message) {
        super(message);
    }
}