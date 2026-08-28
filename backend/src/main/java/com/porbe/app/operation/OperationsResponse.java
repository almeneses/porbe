package com.porbe.app.operation;

import java.util.List;

/** Colección paginable en el futuro con su conteo total de operaciones. */
public record OperationsResponse(long total, List<OperationResponse> operations) {
}
