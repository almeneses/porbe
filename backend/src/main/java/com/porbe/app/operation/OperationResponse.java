package com.porbe.app.operation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Vista de una operación con su impacto calculado sobre el efectivo. */
public record OperationResponse(
        Long id,
        LocalDate date,
        String type,
        String ticker,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal commission,
        BigDecimal totalAmount,
        BigDecimal cashImpact,
        String notes,
        Long importBatchId,
        String sourceType,
        String sourceFilename,
        String importedBy,
        OffsetDateTime importedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String updatedBy,
        BigDecimal quantityAfter,
        String consistencyIssue) {

    static OperationResponse from(
            PortfolioOperation operation,
            BigDecimal quantityAfter,
            String consistencyIssue) {
        var batch = operation.getImportBatch();
        return new OperationResponse(
                operation.getId(),
                operation.getDate(),
                operation.getType().label(),
                operation.getTicker(),
                operation.getName(),
                operation.getQuantity(),
                operation.getUnitPrice(),
                operation.getCommission(),
                operation.getTotalAmount(),
                operation.getTotalAmount().multiply(BigDecimal.valueOf(operation.getType().cashSign())),
                operation.getNotes(),
                batch.getId(),
                batch.getSourceType().name(),
                batch.getSourceFilename(),
                batch.getImportedBy(),
                batch.getImportedAt(),
                operation.getCreatedAt(),
                operation.getUpdatedAt(),
                operation.getUpdatedBy(),
                quantityAfter,
                consistencyIssue);
    }
}
