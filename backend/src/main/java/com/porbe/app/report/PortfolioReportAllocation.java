package com.porbe.app.report;

import java.math.BigDecimal;

/** Participación de una acción o sector dentro del valor invertido del portafolio. */
public record PortfolioReportAllocation(
        String key,
        String name,
        BigDecimal rate,
        byte[] icon) {
}
