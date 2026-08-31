package com.porbe.app.operation;

import com.porbe.app.importer.ImportSourceType;
import java.time.LocalDate;

/** Criterios opcionales para consultar o exportar el libro de operaciones. */
public record OperationFilter(
        LocalDate from,
        LocalDate to,
        String ticker,
        OperationType type,
        ImportSourceType sourceType,
        Long importBatchId) {
}
