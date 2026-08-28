package com.porbe.app.importer;

/** Agrupa los errores que impiden aceptar un archivo de portafolio. */
public class PortfolioImportValidationException extends RuntimeException {

    private final PortfolioImportResult result;

    public PortfolioImportValidationException(PortfolioImportResult result) {
        super(result.message());
        this.result = result;
    }

    public PortfolioImportResult getResult() {
        return result;
    }
}
