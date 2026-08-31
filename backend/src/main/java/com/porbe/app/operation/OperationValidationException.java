package com.porbe.app.operation;

import java.util.List;

/** Interrumpe una escritura cuando la operación incumple reglas del portafolio. */
public class OperationValidationException extends RuntimeException {

    private final List<OperationFieldError> errors;

    public OperationValidationException(List<OperationFieldError> errors) {
        super("Revisa los campos de la operación.");
        this.errors = List.copyOf(errors);
    }

    public List<OperationFieldError> getErrors() {
        return errors;
    }
}
