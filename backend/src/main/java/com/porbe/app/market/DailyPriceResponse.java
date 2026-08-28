package com.porbe.app.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Precio diario expuesto por la API para consultas históricas. */
public record DailyPriceResponse(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        Long volume,
        boolean finalClose) {

    static DailyPriceResponse from(MarketPriceDaily price) {
        return new DailyPriceResponse(
                price.getPriceDate(),
                price.getOpen(),
                price.getHigh(),
                price.getLow(),
                price.getClose(),
                price.getAdjustedClose(),
                price.getVolume(),
                price.isFinalClose());
    }
}
