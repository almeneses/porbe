package com.porbe.app.market;

import java.math.BigDecimal;
import java.util.List;

/** Serie normalizada con metadatos del activo y sus velas diarias. */
public record MarketDataSeries(
        String ticker,
        String name,
        String currency,
        String exchange,
        String instrumentType,
        String exchangeTimezone,
        BigDecimal regularMarketPrice,
        List<DailyMarketBar> bars) {
}
