package com.porbe.app.report;

import java.time.OffsetDateTime;

/** Programación visible del informe semanal y estado del canal de entrega. */
public record PortfolioReportScheduleResponse(
        boolean enabled,
        String dayOfWeek,
        String runTime,
        String timezone,
        OffsetDateTime nextRunAt,
        boolean deliveryConfigured,
        String deliveryChannel) {
}
