package com.porbe.app.portfolio;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Serie semanal disponible para gráficos y consultas históricas del portafolio. */
public record PortfolioHistoryResponse(
        OffsetDateTime calculatedAt,
        String baseCurrency,
        LocalDate from,
        LocalDate to,
        LocalDate lastCompletedWeek,
        long operationCount,
        int weekCount,
        boolean valuationComplete,
        BigDecimal totalMoneyWeightedReturn,
        BigDecimal yearMoneyWeightedReturn,
        BigDecimal annualizedMoneyWeightedReturn,
        List<PortfolioWeeklySnapshot> weeks) {
}
