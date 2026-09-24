package io.github.diegofranciscog.accounting.exception;

/** Rate limit exceeded; mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
