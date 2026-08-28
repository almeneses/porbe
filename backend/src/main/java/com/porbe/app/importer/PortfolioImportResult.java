package com.porbe.app.importer;

import java.util.List;

/** Resultado completo de una importación con métricas y errores por fila. */
public record PortfolioImportResult(
        boolean success,
        int totalRows,
        int importedRows,
        Long importId,
        String message,
        List<ImportRowError> errors) {

    static PortfolioImportResult completed(int rows, Long importId) {
        return new PortfolioImportResult(
                true,
                rows,
                rows,
                importId,
                rows == 1 ? "Se importó 1 operación correctamente." : "Se importaron " + rows + " operaciones correctamente.",
                List.of());
    }

    static PortfolioImportResult rejected(int rows, List<ImportRowError> errors) {
        return new PortfolioImportResult(
                false,
                rows,
                0,
                null,
                "No se importó ninguna operación. Corrige los errores y vuelve a cargar el archivo.",
                List.copyOf(errors));
    }
}
