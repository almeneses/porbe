package com.porbe.app.market;

/** Encapsula fallos de red o respuestas inválidas del proveedor externo. */
public class MarketDataProviderException extends RuntimeException {

    public MarketDataProviderException(String message) {
        super(message);
    }

    public MarketDataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
