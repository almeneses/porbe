package com.porbe.app.market;

/** Señala que el activo solicitado todavía no tiene información disponible. */
public class MarketDataNotFoundException extends RuntimeException {

    public MarketDataNotFoundException(String message) {
        super(message);
    }
}
