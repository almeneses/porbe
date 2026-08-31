package com.porbe.app.operation;

import java.util.List;

/** Respuesta estructurada para mostrar errores junto al formulario manual. */
public record OperationValidationResponse(
        String code,
        String message,
        List<OperationFieldError> errors) {
}
