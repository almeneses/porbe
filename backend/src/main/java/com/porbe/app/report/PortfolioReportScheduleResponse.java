package com.porbe.app.report;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;

/** Programación visible del informe semanal y estado del canal de entrega. */
public record PortfolioReportScheduleResponse(
        boolean enabled,
        DayOfWeek dayOfWeek,
        String runTime,
        String timezone,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        String updatedBy,
        OffsetDateTime updatedAt,
        boolean deliveryConfigured,
        String deliveryChannel) {
}
