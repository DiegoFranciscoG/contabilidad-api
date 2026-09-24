package io.github.diegofranciscog.accounting.exception;

/** The request collides with existing data (duplicated code, already reversed entry...); mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
