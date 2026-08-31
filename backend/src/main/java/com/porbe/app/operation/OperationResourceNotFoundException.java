package com.porbe.app.operation;

/** Indica que una operación o lote solicitado ya no existe. */
public class OperationResourceNotFoundException extends RuntimeException {

    public OperationResourceNotFoundException(String message) {
        super(message);
    }
}
