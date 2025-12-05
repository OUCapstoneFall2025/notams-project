package ou.capstone.notams.exceptions;

public class RateLimitException extends NotamException {
    public RateLimitException(String message) {
        super(message);
    }
}