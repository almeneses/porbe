package com.porbe.app.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos financieros normalizados que alimentan todos los formatos del informe. */
public record PortfolioReportData(
        LocalDate from,
        LocalDate to,
        LocalDate baselineDate,
        LocalDate valuationDate,
        String baseCurrency,
        BigDecimal periodGain,
        BigDecimal periodReturn,
        PortfolioReportAssetHighlight bestAppreciation,
        PortfolioReportAssetHighlight worstAppreciation,
        PortfolioReportAssetHighlight bestProfitability,
        PortfolioReportAssetHighlight worstProfitability,
        BigDecimal accumulatedDividends,
        BigDecimal accumulatedGain,
        BigDecimal accumulatedReturn,
        BigDecimal netContributions,
        BigDecimal cashBalance,
        BigDecimal portfolioValue,
        int movementCount,
        List<PortfolioReportMovement> movements,
        List<PortfolioReportChartPoint> chart,
        boolean valuationComplete,
        int provisionalPrices,
        int unpricedPositions) {
}
