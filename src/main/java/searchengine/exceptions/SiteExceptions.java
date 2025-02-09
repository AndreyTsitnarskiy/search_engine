package searchengine.exceptions;

public class SiteExceptions extends RuntimeException {

    public SiteExceptions(String message) {
        super(message);
    }

    public SiteExceptions(String message, Throwable cause) {
        super(message, cause);
    }
}
