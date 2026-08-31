package com.porbe.app.report;

/** Indica que el informe solicitado no existe o aún no produjo el formato pedido. */
public class PortfolioReportNotFoundException extends RuntimeException {

    public PortfolioReportNotFoundException(String message) {
        super(message);
    }
}
