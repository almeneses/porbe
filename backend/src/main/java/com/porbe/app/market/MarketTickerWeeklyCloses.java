package com.porbe.app.market;

import java.util.List;

/** Serie de viernes para uno de los tickers registrados en el portafolio. */
public record MarketTickerWeeklyCloses(
        String ticker,
        String name,
        String currency,
        String sector,
        List<MarketWeeklyClosePoint> closes) {
}
