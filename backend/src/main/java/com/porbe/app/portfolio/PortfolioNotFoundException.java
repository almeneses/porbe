package com.porbe.app.portfolio;

/** Indica que el portafolio solicitado no pertenece al conjunto disponible. */
public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(String message) {
        super(message);
    }
}
