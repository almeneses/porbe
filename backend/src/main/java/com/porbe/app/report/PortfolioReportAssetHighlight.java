package com.porbe.app.report;

import java.math.BigDecimal;

/** Activo destacado por valorización del periodo o rentabilidad acumulada. */
public record PortfolioReportAssetHighlight(
        String ticker,
        String name,
        BigDecimal rate,
        BigDecimal amount,
        byte[] icon) {
}
