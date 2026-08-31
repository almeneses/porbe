package com.porbe.app.market;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Matriz de cierres semanales desde la fecha base del histórico. */
public record MarketWeeklyClosesResponse(
        OffsetDateTime calculatedAt,
        LocalDate from,
        LocalDate to,
        int tickerCount,
        int weekCount,
        boolean complete,
        List<MarketTickerWeeklyCloses> tickers) {
}
