package com.porbe.app.market;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** Estado visible y próxima ejecución de la actualización programada. */
public record MarketDataScheduleResponse(
        boolean enabled,
        DayOfWeek dayOfWeek,
        LocalTime runTime,
        String timezone,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        String updatedBy,
        OffsetDateTime updatedAt) {
}
