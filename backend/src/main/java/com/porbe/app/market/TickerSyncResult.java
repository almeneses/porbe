package com.porbe.app.market;

/** Resultado individual de actualizar un ticker contra el proveedor. */
public record TickerSyncResult(String ticker, boolean success, int storedPrices, String message) {
}
