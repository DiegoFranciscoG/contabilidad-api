package io.github.diegofranciscog.accounting.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " no encontrado: " + id);
    }
}
