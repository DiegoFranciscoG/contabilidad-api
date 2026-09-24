package io.github.diegofranciscog.accounting.exception;

/** A business rule was violated; mapped to HTTP 422. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
