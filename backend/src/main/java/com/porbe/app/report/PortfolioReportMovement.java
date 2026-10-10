package com.porbe.app.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Movimiento resumido que se incluye en la composición visual. */
public record PortfolioReportMovement(
        LocalDate date,
        String type,
        String ticker,
        String name,
        BigDecimal quantity,
        BigDecimal totalAmount,
        byte[] icon,
        String currency) {

    public PortfolioReportMovement(LocalDate date, String type, String ticker, String name,
            BigDecimal quantity, BigDecimal totalAmount, byte[] icon) {
        this(date, type, ticker, name, quantity, totalAmount, icon, "COP");
    }
}
