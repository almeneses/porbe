package com.porbe.app.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Día, hora y zona horaria elegidos para el informe automático. */
public record PortfolioReportScheduleRequest(
        boolean enabled,
        @NotNull(message = "Selecciona un día de la semana.") DayOfWeek dayOfWeek,
        @NotNull(message = "Selecciona una hora.") LocalTime runTime,
        @NotBlank(message = "Indica una zona horaria.")
        @Size(max = 80, message = "La zona horaria es demasiado larga.") String timezone) {
}
