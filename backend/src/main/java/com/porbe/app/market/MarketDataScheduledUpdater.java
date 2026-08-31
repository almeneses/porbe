package com.porbe.app.market;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sondea la programación persistida y dispara la actualización cuando vence. */
@Component
public class MarketDataScheduledUpdater {

    private final MarketDataScheduleService scheduleService;
    private final MarketDataSyncService syncService;

    public MarketDataScheduledUpdater(
            MarketDataScheduleService scheduleService,
            MarketDataSyncService syncService) {
        this.scheduleService = scheduleService;
        this.syncService = syncService;
    }

    @Scheduled(
            initialDelayString = "${app.market-data.schedule-initial-delay-ms:30000}",
            fixedDelayString = "${app.market-data.schedule-poll-ms:60000}")
    public void updateIfDue() {
        var claim = scheduleService.claimIfDue();
        if (claim.isEmpty()) {
            return;
        }
        try {
            var result = syncService.syncPortfolio();
            var status = result.successfulTickers() == result.totalTickers() ? "SUCCESS" : "PARTIAL";
            scheduleService.finish(claim.get(), status, result.message());
        } catch (RuntimeException exception) {
            var message = exception.getMessage() == null
                    ? "No fue posible completar la actualización automática."
                    : exception.getMessage();
            scheduleService.finish(claim.get(), "FAILED", message);
        }
    }
}
