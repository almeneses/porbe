package com.porbe.app.report;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.porbe.app.portfolio.Portfolio;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioReportDeliveryTest {

    @Test
    void attemptsEveryActiveRecipientAndRecordsPartialFailure() {
        var repository = mock(PortfolioReportRepository.class);
        var deliveryProvider = mock(PortfolioReportDeliveryProvider.class);
        var recipientService = mock(WhatsAppRecipientService.class);
        var report = mock(PortfolioReport.class);
        var portfolio = mock(Portfolio.class);
        var first = new WhatsAppRecipient("Primero", "573001111111", true, "test");
        var second = new WhatsAppRecipient("Segundo", "573002222222", true, "test");
        var service = new PortfolioReportService(
                mock(PortfolioReportCalculator.class),
                mock(PortfolioReportAiNoteService.class),
                mock(PortfolioReportScheduleService.class),
                mock(PortfolioReportArtifactRenderer.class),
                repository,
                deliveryProvider,
                recipientService,
                mock(com.porbe.app.portfolio.PortfolioService.class),
                Clock.systemUTC());

        when(repository.findById(7L)).thenReturn(Optional.of(report));
        when(repository.save(report)).thenReturn(report);
        when(report.getStatus()).thenReturn("READY");
        when(report.getPortfolio()).thenReturn(portfolio);
        when(portfolio.getId()).thenReturn(1L);
        when(recipientService.active()).thenReturn(List.of(first, second));
        when(deliveryProvider.configured()).thenReturn(true);
        when(deliveryProvider.deliver(report, first.getPhoneNumber()))
                .thenThrow(new WhatsAppDeliveryException("falló"));
        when(deliveryProvider.deliver(report, second.getPhoneNumber()))
                .thenReturn(new PortfolioReportDeliveryResult("SENT", "enviado"));

        service.deliverToActiveRecipients(7L);

        verify(deliveryProvider).deliver(report, first.getPhoneNumber());
        verify(deliveryProvider).deliver(report, second.getPhoneNumber());
        verify(report).markDelivery("FAILED", "Informe enviado a 1 de 2 destinatario(s).");
    }
}
