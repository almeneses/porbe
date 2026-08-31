package com.porbe.app.operation;

/** Acciones persistidas en la bitácora de administración del portafolio. */
public enum OperationAuditAction {
    CREATED,
    UPDATED,
    DELETED,
    BATCH_REVERTED
}
