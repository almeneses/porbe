package com.porbe.app.operation;

import java.time.OffsetDateTime;

/** Lote importado que puede revertirse de forma atómica desde la UI. */
public record OperationBatchResponse(
        Long id,
        String sourceFilename,
        int importedRows,
        long currentOperations,
        String importedBy,
        OffsetDateTime importedAt) {
}
