package com.porbe.app.operation;

import java.util.List;

/** Colección filtrada con conteo total y límite aplicado a la respuesta. */
public record OperationsResponse(long total, int returned, List<OperationResponse> operations) {
}
