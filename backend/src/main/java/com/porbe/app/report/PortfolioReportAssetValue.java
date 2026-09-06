package com.porbe.app.report;

import java.math.BigDecimal;

/** Monto acumulado asociado a una acción dentro del informe. */
public record PortfolioReportAssetValue(
        String ticker,
        String name,
        BigDecimal amount) {
}
