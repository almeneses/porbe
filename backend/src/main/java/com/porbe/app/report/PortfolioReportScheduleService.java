package com.porbe.app.report;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Expone la siguiente ejecución calculada a partir de la configuración del backend. */
@Service
public class PortfolioReportScheduleService {

    private final boolean enabled;
    private final String cron;
    private final String timezone;
    private final PortfolioReportService reportService;
    private final Clock clock;

    public PortfolioReportScheduleService(
            @Value("${app.reports.weekly-enabled:true}") boolean enabled,
            @Value("${app.reports.weekly-cron:0 30 17 * * FRI}") String cron,
            @Value("${app.reports.timezone:America/Bogota}") String timezone,
            PortfolioReportService reportService,
            Clock clock) {
        this.enabled = enabled;
        this.cron = cron;
        this.timezone = timezone;
        this.reportService = reportService;
        this.clock = clock;
    }

    public PortfolioReportScheduleResponse current() {
        var zone = ZoneId.of(timezone);
        var now = ZonedDateTime.ofInstant(clock.instant(), zone);
        var next = enabled ? CronExpression.parse(cron).next(now) : null;
        return new PortfolioReportScheduleResponse(
                enabled,
                "FRIDAY",
                "17:30",
                timezone,
                next == null ? null : next.toOffsetDateTime(),
                reportService.deliveryConfigured(),
                reportService.deliveryChannel());
    }
}
