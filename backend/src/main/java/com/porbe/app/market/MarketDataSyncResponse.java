package com.porbe.app.market;

import java.util.List;

/** Resultado agregado de una sincronización solicitada por el usuario. */
public record MarketDataSyncResponse(
        int totalTickers,
        int successfulTickers,
        int storedPrices,
        String message,
        List<TickerSyncResult> results) {
}
