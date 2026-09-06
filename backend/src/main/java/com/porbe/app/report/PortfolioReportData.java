package com.porbe.app.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos financieros normalizados que alimentan todos los formatos del informe. */
public record PortfolioReportData(
        Long portfolioId,
        String portfolioName,
        LocalDate from,
        LocalDate to,
        LocalDate baselineDate,
        LocalDate valuationDate,
        String baseCurrency,
        BigDecimal periodGain,
        BigDecimal periodReturn,
        PortfolioReportAssetHighlight bestPeriodImpact,
        PortfolioReportAssetHighlight worstPeriodImpact,
        BigDecimal accumulatedDividends,
        BigDecimal accumulatedGain,
        BigDecimal accumulatedReturn,
        BigDecimal timeWeightedReturn,
        BigDecimal netContributions,
        BigDecimal cashBalance,
        BigDecimal portfolioValue,
        int movementCount,
        List<PortfolioReportMovement> movements,
        List<PortfolioReportChartPoint> chart,
        List<PortfolioReportAssetValue> gainsByAsset,
        List<PortfolioReportAssetValue> dividendsByAsset,
        List<PortfolioReportAllocation> assetAllocation,
        List<PortfolioReportAllocation> sectorAllocation,
        boolean valuationComplete,
        int provisionalPrices,
        int unpricedPositions) {
}
