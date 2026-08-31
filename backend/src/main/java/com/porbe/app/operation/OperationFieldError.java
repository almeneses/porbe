package com.porbe.app.operation;

/** Error de una regla financiera asociado a un campo editable. */
public record OperationFieldError(String field, String message) {
}
