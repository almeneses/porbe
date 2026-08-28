package com.porbe.app.market;

import java.util.List;

/** Serie de precios diarios devuelta al cliente para un único ticker. */
public record TickerPricesResponse(
        String ticker,
        String name,
        String currency,
        String exchange,
        List<DailyPriceResponse> prices) {
}
