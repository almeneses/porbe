package com.porbe.app.market;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Día y hora elegidos para la actualización automática semanal. */
public record MarketDataScheduleRequest(
        boolean enabled,
        @NotNull(message = "Selecciona un día de la semana.") DayOfWeek dayOfWeek,
        @NotNull(message = "Selecciona una hora.") LocalTime runTime) {
}
