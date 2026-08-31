package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Posición acumulada de un ticker, valorada con el último precio disponible. */
public record PortfolioPositionResponse(
        String ticker,
        String name,
        String currency,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal totalPurchases,
        BigDecimal lastPrice,
        LocalDate priceDate,
        boolean provisionalPrice,
        BigDecimal marketValue,
        BigDecimal realizedGain,
        BigDecimal unrealizedGain,
        BigDecimal dividends,
        BigDecimal totalGain,
        BigDecimal returnRate,
        boolean closed,
        boolean valued,
        boolean calculationComplete,
        boolean foreignCurrency) {
}
