package com.porbe.app.operation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Representación normalizada compartida por la importación y la edición manual. */
public record OperationData(
        LocalDate date,
        OperationType type,
        String ticker,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal commission,
        BigDecimal totalAmount,
        String notes) {
}
