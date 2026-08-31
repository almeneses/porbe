package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Estado de un ticker al cierre de una semana del portafolio. */
public record PortfolioWeeklyPositionResponse(
        String ticker,
        String name,
        String currency,
        String sector,
        BigDecimal quantity,
        BigDecimal closePrice,
        LocalDate priceDate,
        boolean provisionalPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal dividends,
        BigDecimal totalGain,
        boolean valued,
        boolean calculationComplete,
        boolean foreignCurrency) {
}
