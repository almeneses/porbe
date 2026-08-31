package com.porbe.app.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Punto semanal usado para comparar valor del portafolio y aportes netos. */
public record PortfolioReportChartPoint(
        LocalDate date,
        BigDecimal portfolioValue,
        BigDecimal netContributions) {
}
