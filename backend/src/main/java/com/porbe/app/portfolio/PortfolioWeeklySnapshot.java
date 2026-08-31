package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Consolidado del portafolio y sus posiciones para un viernes determinado. */
public record PortfolioWeeklySnapshot(
        LocalDate weekEnding,
        BigDecimal marketValue,
        BigDecimal investedCapital,
        BigDecimal netContributions,
        BigDecimal dividends,
        BigDecimal cashBalance,
        BigDecimal portfolioValue,
        BigDecimal realizedGain,
        BigDecimal unrealizedGain,
        BigDecimal totalGain,
        BigDecimal returnRate,
        BigDecimal externalCashFlow,
        BigDecimal periodReturn,
        BigDecimal timeWeightedReturn,
        BigDecimal annualizedReturn,
        BigDecimal nominalVariation,
        BigDecimal percentageVariation,
        boolean valuationComplete,
        int unpricedPositions,
        int foreignCurrencyPositions,
        int inconsistentPositions,
        List<PortfolioWeeklyPositionResponse> positions) {

    /** Agrega métricas TWR una vez conocido el valor final del cierre. */
    public PortfolioWeeklySnapshot withPerformance(
            BigDecimal externalCashFlow,
            BigDecimal periodReturn,
            BigDecimal timeWeightedReturn,
            BigDecimal annualizedReturn) {
        return new PortfolioWeeklySnapshot(
                weekEnding,
                marketValue,
                investedCapital,
                netContributions,
                dividends,
                cashBalance,
                portfolioValue,
                realizedGain,
                unrealizedGain,
                totalGain,
                returnRate,
                externalCashFlow,
                periodReturn,
                timeWeightedReturn,
                annualizedReturn,
                nominalVariation,
                percentageVariation,
                valuationComplete,
                unpricedPositions,
                foreignCurrencyPositions,
                inconsistentPositions,
                positions);
    }
}
