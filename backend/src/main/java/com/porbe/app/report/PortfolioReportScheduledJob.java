package com.porbe.app.report;

import com.porbe.app.market.MarketDataSyncService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Actualiza precios y genera el informe semanal sin depender de una sesión web. */
@Component
public class PortfolioReportScheduledJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioReportScheduledJob.class);

    private final boolean enabled;
    private final ZoneId timezone;
    private final MarketDataSyncService marketDataSyncService;
    private final PortfolioReportService reportService;
    private final Clock clock;

    public PortfolioReportScheduledJob(
            @Value("${app.reports.weekly-enabled:true}") boolean enabled,
            @Value("${app.reports.timezone:America/Bogota}") String timezone,
            MarketDataSyncService marketDataSyncService,
            PortfolioReportService reportService,
            Clock clock) {
        this.enabled = enabled;
        this.timezone = ZoneId.of(timezone);
        this.marketDataSyncService = marketDataSyncService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.reports.weekly-cron:0 30 17 * * FRI}",
            zone = "${app.reports.timezone:America/Bogota}")
    public void generateWeeklyReport() {
        if (!enabled) {
            return;
        }
        var to = LocalDate.ofInstant(clock.instant(), timezone);
        var from = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        try {
            marketDataSyncService.syncPortfolio();
            var report = reportService.generateScheduledIfMissing(from, to);
            reportService.deliver(report.id());
        } catch (RuntimeException exception) {
            LOGGER.error("No fue posible generar el informe semanal del {} al {}.", from, to, exception);
        }
    }
}
