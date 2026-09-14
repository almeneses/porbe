package com.porbe.app.report;

import com.porbe.app.market.MarketDataSyncService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Actualiza precios y genera el informe semanal sin depender de una sesión web. */
@Component
public class PortfolioReportScheduledJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioReportScheduledJob.class);

    private final PortfolioReportScheduleService scheduleService;
    private final MarketDataSyncService marketDataSyncService;
    private final PortfolioReportService reportService;
    private final com.porbe.app.portfolio.PortfolioService portfolioService;
    private final Clock clock;

    public PortfolioReportScheduledJob(
            PortfolioReportScheduleService scheduleService,
            MarketDataSyncService marketDataSyncService,
            PortfolioReportService reportService,
            com.porbe.app.portfolio.PortfolioService portfolioService,
            Clock clock) {
        this.scheduleService = scheduleService;
        this.marketDataSyncService = marketDataSyncService;
        this.reportService = reportService;
        this.portfolioService = portfolioService;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.reports.schedule-initial-delay-ms:45000}",
            fixedDelayString = "${app.reports.schedule-poll-ms:60000}")
    public void generateWeeklyReport() {
        var claim = scheduleService.claimIfDue();
        if (claim.isEmpty()) {
            return;
        }
        var to = LocalDate.ofInstant(clock.instant(), claim.get());
        var from = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        try {
            var sync = marketDataSyncService.syncPortfolio();
            var portfolios = portfolioService.listForScheduledReport();
            var deliveredAll = true;
            for (var portfolio : portfolios) {
                var report = reportService.generateScheduledIfMissing(portfolio.id(), from, to);
                var delivered = reportService.deliverToActiveRecipients(report.id());
                deliveredAll = deliveredAll && "SENT".equals(delivered.deliveryStatus());
            }
            var status = sync.successfulTickers() == sync.totalTickers() && deliveredAll ? "SUCCESS" : "PARTIAL";
            scheduleService.finish(status, "Informe semanal generado y entrega procesada para "
                    + portfolios.size() + " portafolio(s).");
        } catch (RuntimeException exception) {
            var message = exception.getMessage() == null
                    ? "No fue posible completar el informe automático."
                    : exception.getMessage();
            scheduleService.finish("FAILED", message);
            LOGGER.error("No fue posible generar el informe semanal del {} al {}.", from, to, exception);
        }
    }
}
