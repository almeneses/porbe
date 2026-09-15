package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    static PortfolioWeeklyPositionResponse from(PortfolioPositionResponse position) {
        return new PortfolioWeeklyPositionResponse(
                position.ticker(), position.name(), position.currency(), position.sector(),
                position.quantity(), position.lastPrice(), position.priceDate(), position.provisionalPrice(),
                position.marketValue(), position.costBasis(), position.dividends(), position.totalGain(),
                position.valued(), position.calculationComplete(), position.foreignCurrency());
    }
}
