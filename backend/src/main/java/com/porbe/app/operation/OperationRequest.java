package com.porbe.app.operation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos recibidos al crear o modificar una operación desde la interfaz. */
public record OperationRequest(
        LocalDate date,
        String type,
        String ticker,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal commission,
        BigDecimal totalAmount,
        String notes,
        String currency) {

    public OperationRequest(LocalDate date, String type, String ticker, String name,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal commission, BigDecimal totalAmount, String notes) {
        this(date, type, ticker, name, quantity, unitPrice, commission, totalAmount, notes, "COP");
    }
}
