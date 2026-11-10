package com.porbe.app.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.porbe.app.market.MarketDataSyncResponse;
import com.porbe.app.market.MarketDataSyncService;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.portfolio.PortfolioResponse;
import com.porbe.app.portfolio.PortfolioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PortfolioReportScheduledJobTest {

    @ParameterizedTest(name = "Ejecución {0}: semana cerrada {1} a {2}")
    @CsvSource({
        "2026-01-19, 2026-01-12, 2026-01-16",
        "2026-01-20, 2026-01-12, 2026-01-16",
        "2026-01-21, 2026-01-12, 2026-01-16",
        "2026-01-22, 2026-01-12, 2026-01-16",
        "2026-01-23, 2026-01-19, 2026-01-23",
        "2026-01-24, 2026-01-19, 2026-01-23",
        "2026-01-25, 2026-01-19, 2026-01-23"
    })
    void generatesTheLastClosedWeek(String today, String from, String to) {
        var zone = ZoneId.of("America/Bogota");
        var clock = Clock.fixed(LocalDate.parse(today).atTime(18, 0).atZone(zone).toInstant(), zone);
        var schedule = mock(PortfolioReportScheduleService.class);
        when(schedule.claimIfDue()).thenReturn(Optional.of(zone));
        var market = mock(MarketDataSyncService.class);
        when(market.syncPortfolio()).thenReturn(new MarketDataSyncResponse(0, 0, 0, "", List.of()));
        var portfolios = mock(PortfolioService.class);
        when(portfolios.listForScheduledReport()).thenReturn(List.of(
                new PortfolioResponse(1L, "Prueba", "COP", true, null, null)));
        var operations = mock(PortfolioOperationRepository.class);
        when(operations.existsByPortfolioId(1L)).thenReturn(true);
        var reports = mock(PortfolioReportService.class);
        var report = mock(PortfolioReportListItem.class);
        when(report.id()).thenReturn(7L);
        when(report.deliveryStatus()).thenReturn("SENT");
        when(reports.generateScheduledIfMissing(eq(1L), any(), any())).thenReturn(report);
        when(reports.deliverToActiveRecipients(7L)).thenReturn(report);

        new PortfolioReportScheduledJob(schedule, market, reports, portfolios, operations, clock)
                .generateWeeklyReport();

        verify(reports).generateScheduledIfMissing(1L, LocalDate.parse(from), LocalDate.parse(to));
        verify(schedule).finish(eq("SUCCESS"), any());
    }

    @Test
    void skipsPortfoliosWithoutOperationsWithoutBlockingTheRest() {
        var zone = ZoneId.of("America/Bogota");
        var clock = Clock.fixed(LocalDate.parse("2026-01-23").atTime(18, 0).atZone(zone).toInstant(), zone);
        var schedule = mock(PortfolioReportScheduleService.class);
        when(schedule.claimIfDue()).thenReturn(Optional.of(zone));
        var market = mock(MarketDataSyncService.class);
        when(market.syncPortfolio()).thenReturn(new MarketDataSyncResponse(0, 0, 0, "", List.of()));
        var portfolios = mock(PortfolioService.class);
        when(portfolios.listForScheduledReport()).thenReturn(List.of(
                new PortfolioResponse(1L, "Con operaciones", "COP", true, null, null),
                new PortfolioResponse(2L, "Vacío", "COP", true, null, null)));
        var operations = mock(PortfolioOperationRepository.class);
        when(operations.existsByPortfolioId(1L)).thenReturn(true);
        when(operations.existsByPortfolioId(2L)).thenReturn(false);
        var reports = mock(PortfolioReportService.class);
        var report = mock(PortfolioReportListItem.class);
        when(report.id()).thenReturn(7L);
        when(report.deliveryStatus()).thenReturn("SENT");
        when(reports.generateScheduledIfMissing(eq(1L), any(), any())).thenReturn(report);
        when(reports.deliverToActiveRecipients(7L)).thenReturn(report);

        new PortfolioReportScheduledJob(schedule, market, reports, portfolios, operations, clock)
                .generateWeeklyReport();

        verify(reports).generateScheduledIfMissing(eq(1L), any(), any());
        verify(reports, never()).generateScheduledIfMissing(eq(2L), any(), any());
        verify(schedule).finish(eq("SUCCESS"), contains("Se omitieron 1 sin operaciones"));
    }
}
