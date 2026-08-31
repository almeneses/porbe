package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Resumen actual del portafolio y detalle de sus posiciones por ticker. */
public record PortfolioSummaryResponse(
        OffsetDateTime calculatedAt,
        LocalDate valuationDate,
        String baseCurrency,
        long operationCount,
        int openPositionCount,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal cashBalance,
        BigDecimal portfolioValue,
        BigDecimal netContributions,
        BigDecimal dividends,
        BigDecimal realizedGain,
        BigDecimal unrealizedGain,
        BigDecimal totalGain,
        BigDecimal returnRate,
        boolean valuationComplete,
        int unpricedPositions,
        int foreignCurrencyPositions,
        List<PortfolioPositionResponse> positions,
        List<PortfolioValuationIssue> issues) {
}
