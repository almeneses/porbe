package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Posición acumulada de un ticker, valorada con el último precio disponible. */
public record PortfolioPositionResponse(
        String ticker,
        String name,
        String currency,
        String sector,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal totalPurchases,
        BigDecimal lastPrice,
        LocalDate priceDate,
        boolean provisionalPrice,
        BigDecimal marketValue,
        BigDecimal allocationRate,
        BigDecimal realizedGain,
        BigDecimal unrealizedGain,
        BigDecimal dividends,
        BigDecimal totalGain,
        BigDecimal returnRate,
        boolean closed,
        boolean valued,
        boolean calculationComplete,
        boolean foreignCurrency) {

    public PortfolioPositionResponse withAllocationRate(BigDecimal allocationRate) {
        return new PortfolioPositionResponse(
                ticker,
                name,
                currency,
                sector,
                quantity,
                averageCost,
                costBasis,
                totalPurchases,
                lastPrice,
                priceDate,
                provisionalPrice,
                marketValue,
                allocationRate,
                realizedGain,
                unrealizedGain,
                dividends,
                totalGain,
                returnRate,
                closed,
                valued,
                calculationComplete,
                foreignCurrency);
    }
}
