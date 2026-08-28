package com.porbe.app.market;

import java.time.OffsetDateTime;
import java.util.List;

/** Resumen de cobertura disponible para todos los activos del portafolio. */
public record MarketDataStatusResponse(
        String source,
        OffsetDateTime checkedAt,
        List<MarketTickerStatus> tickers) {
}
