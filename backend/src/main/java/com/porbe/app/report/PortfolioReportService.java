package com.porbe.app.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/** Coordina cálculo, renderizado, persistencia, descarga y entrega de informes. */
@Service
public class PortfolioReportService {

    private final PortfolioReportCalculator calculator;
    private final PortfolioReportArtifactRenderer renderer;
    private final PortfolioReportRepository repository;
    private final PortfolioReportDeliveryProvider deliveryProvider;
    private final Clock clock;

    public PortfolioReportService(
            PortfolioReportCalculator calculator,
            PortfolioReportArtifactRenderer renderer,
            PortfolioReportRepository repository,
            PortfolioReportDeliveryProvider deliveryProvider,
            Clock clock) {
        this.calculator = calculator;
        this.renderer = renderer;
        this.repository = repository;
        this.deliveryProvider = deliveryProvider;
        this.clock = clock;
    }

    public PortfolioReportListItem generate(
            LocalDate from,
            LocalDate to,
            String triggerType,
            String generatedBy) {
        var data = calculator.calculate(from, to);
        var report = repository.save(new PortfolioReport(data, triggerType, generatedBy));
        try {
            var artifacts = renderer.render(data);
            report.markReady(
                    artifacts.image(),
                    artifacts.pdf(),
                    OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            return item(repository.save(report));
        } catch (RuntimeException exception) {
            report.markFailed(exception.getMessage());
            repository.save(report);
            throw exception;
        }
    }

    public PortfolioReportListItem generateScheduledIfMissing(LocalDate from, LocalDate to) {
        return repository.findFirstByFromAndToAndTriggerTypeAndStatusOrderByCreatedAtDesc(
                        from, to, "SCHEDULED", "READY")
                .map(this::item)
                .orElseGet(() -> generate(from, to, "SCHEDULED", "system"));
    }

    public void deliver(Long reportId) {
        var report = report(reportId);
        var result = deliveryProvider.deliver(report);
        report.markDelivery(result.status(), result.message());
        repository.save(report);
    }

    public List<PortfolioReportListItem> list() {
        return repository.listRecent();
    }

    public PortfolioReportFile image(Long id) {
        var report = report(id);
        if (report.getImageData() == null) {
            throw new PortfolioReportNotFoundException("El informe todavía no tiene una imagen disponible.");
        }
        return new PortfolioReportFile(
                report.getImageData(),
                "image/png",
                filename(report, "png"));
    }

    public PortfolioReportFile pdf(Long id) {
        var report = report(id);
        if (report.getPdfData() == null) {
            throw new PortfolioReportNotFoundException("El informe todavía no tiene un PDF disponible.");
        }
        return new PortfolioReportFile(
                report.getPdfData(),
                "application/pdf",
                filename(report, "pdf"));
    }

    public boolean deliveryConfigured() {
        return deliveryProvider.configured();
    }

    private PortfolioReport report(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PortfolioReportNotFoundException("El informe solicitado no existe."));
    }

    private String filename(PortfolioReport report, String extension) {
        return "informe_portafolio_" + report.getFrom() + "_" + report.getTo() + "." + extension;
    }

    private PortfolioReportListItem item(PortfolioReport report) {
        return new PortfolioReportListItem(
                report.getId(),
                report.getFrom(),
                report.getTo(),
                report.getValuationDate(),
                report.getBaseCurrency(),
                report.getTriggerType(),
                report.getStatus(),
                report.getGeneratedBy(),
                report.isValuationComplete(),
                report.getProvisionalPrices(),
                report.getImageSize(),
                report.getPdfSize(),
                report.getDeliveryStatus(),
                report.getDeliveryMessage(),
                report.getErrorMessage(),
                report.getGeneratedAt(),
                report.getCreatedAt());
    }
}
