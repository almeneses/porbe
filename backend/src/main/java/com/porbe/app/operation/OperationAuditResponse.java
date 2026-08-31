package com.porbe.app.operation;

import java.time.OffsetDateTime;

/** Entrada resumida de la bitácora visible en la interfaz. */
public record OperationAuditResponse(
        Long id,
        Long operationId,
        Long importBatchId,
        String action,
        String username,
        String details,
        OffsetDateTime createdAt) {

    static OperationAuditResponse from(OperationAudit audit) {
        return new OperationAuditResponse(
                audit.getId(),
                audit.getOperationId(),
                audit.getImportBatchId(),
                audit.getAction().name(),
                audit.getUsername(),
                audit.getDetails(),
                audit.getCreatedAt());
    }
}
