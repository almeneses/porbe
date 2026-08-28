package com.porbe.app.operation;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        String notes) {

    static OperationResponse from(PortfolioOperation operation) {
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
                operation.getNotes());
    }
}
