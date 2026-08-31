package com.porbe.app.report;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Periodo solicitado por el usuario para un nuevo informe. */
public record PortfolioReportRequest(
        @NotNull(message = "Selecciona una fecha inicial.") LocalDate from,
        @NotNull(message = "Selecciona una fecha final.") LocalDate to) {
}
