package com.porbe.app.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordina cálculo, renderizado, persistencia, descarga y entrega de informes. */
@Service
public class PortfolioReportService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PortfolioReportService.class);

    private final PortfolioReportCalculator calculator;
    private final PortfolioReportAiNoteService aiNoteService;
    private final PortfolioReportScheduleService scheduleService;
    private final PortfolioReportArtifactRenderer renderer;
    private final PortfolioReportRepository repository;
    private final PortfolioReportDeliveryProvider deliveryProvider;
    private final WhatsAppRecipientService recipientService;
    private final com.porbe.app.portfolio.PortfolioService portfolioService;
    private final Clock clock;

    public PortfolioReportService(
            PortfolioReportCalculator calculator,
            PortfolioReportAiNoteService aiNoteService,
            PortfolioReportScheduleService scheduleService,
            PortfolioReportArtifactRenderer renderer,
            PortfolioReportRepository repository,
            PortfolioReportDeliveryProvider deliveryProvider,
            WhatsAppRecipientService recipientService,
            com.porbe.app.portfolio.PortfolioService portfolioService,
            Clock clock) {
        this.calculator = calculator;
        this.aiNoteService = aiNoteService;
        this.scheduleService = scheduleService;
        this.renderer = renderer;
        this.repository = repository;
        this.deliveryProvider = deliveryProvider;
        this.recipientService = recipientService;
        this.portfolioService = portfolioService;
        this.clock = clock;
    }

    public PortfolioReportListItem generate(
            Long portfolioId,
            LocalDate from,
            LocalDate to,
            String triggerType,
            String generatedBy) {
        var portfolio = portfolioService.getPortfolio(portfolioId);
        var data = calculator.calculate(portfolio.getId(), from, to);
        var report = repository.save(new PortfolioReport(portfolio, data, triggerType, generatedBy));
        try {
            var artifacts = renderer.render(data, aiNoteService.create(data, scheduleService.aiSettings()));
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

    public PortfolioReportListItem generate(
            LocalDate from,
            LocalDate to,
            String triggerType,
            String generatedBy) {
        return generate(null, from, to, triggerType, generatedBy);
    }

    public PortfolioReportListItem generateScheduledIfMissing(Long portfolioId, LocalDate from, LocalDate to) {
        var portfolio = portfolioService.getPortfolio(portfolioId);
        return repository.findFirstByPortfolioAndFromAndToAndTriggerTypeAndStatusOrderByCreatedAtDesc(
                        portfolio, from, to, "SCHEDULED", "READY")
                .map(this::item)
                .orElseGet(() -> generate(portfolio.getId(), from, to, "SCHEDULED", "system"));
    }

    public PortfolioReportListItem deliver(Long reportId, Long recipientId) {
        return deliver(reportId, recipientService.active(recipientId).getPhoneNumber());
    }

    /** Registra el intento antes de llamar al servicio externo y conserva el resultado legible. */
    private PortfolioReportListItem deliver(Long reportId, String recipient) {
        var report = readyReport(reportId);
        report.markDelivery("PENDING", "Enviando el informe por WhatsApp Web…");
        repository.save(report);
        try {
            var result = deliveryProvider.deliver(report, recipient);
            report.markDelivery(result.status(), result.message());
            return item(repository.save(report));
        } catch (RuntimeException exception) {
            report.markDelivery("FAILED", exception.getMessage());
            repository.save(report);
            throw exception;
        }
    }

    /** Entrega el mismo informe a todos los destinatarios activos y conserva un resumen único. */
    public PortfolioReportListItem deliverToActiveRecipients(Long reportId) {
        var report = readyReport(reportId);
        var recipients = recipientService.active();
        if (recipients.isEmpty() || !deliveryProvider.configured()) {
            var message = recipients.isEmpty()
                    ? "No hay destinatarios de WhatsApp activos."
                    : "El servicio de WhatsApp Web está desactivado.";
            report.markDelivery("NOT_CONFIGURED", message);
            return item(repository.save(report));
        }

        report.markDelivery("PENDING", "Enviando el informe por WhatsApp Web…");
        repository.save(report);
        var sent = 0;
        for (var recipient : recipients) {
            try {
                if ("SENT".equals(deliveryProvider.deliver(report, recipient.getPhoneNumber()).status())) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("No fue posible entregar el informe {} al destinatario {}.",
                        reportId, recipient.getId(), exception);
            }
        }
        var status = sent == recipients.size() ? "SENT" : "FAILED";
        report.markDelivery(status, "Informe enviado a " + sent + " de " + recipients.size() + " destinatario(s).");
        return item(repository.save(report));
    }

    public PortfolioReportListItem testDelivery(Long recipientId) {
        var report = repository.findFirstByStatusOrderByCreatedAtDesc("READY")
                .orElseThrow(() -> new IllegalArgumentException("Genera al menos un informe antes de probar el envío."));
        return deliver(report.getId(), recipientId);
    }

    public List<PortfolioReportListItem> list(Long portfolioId) {
        return repository.listRecent(portfolioService.getPortfolio(portfolioId));
    }

    public List<PortfolioReportListItem> list() {
        return list(null);
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

    public WhatsAppConnectionStatus whatsAppStatus() {
        return deliveryProvider.connectionStatus();
    }

    private PortfolioReport report(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PortfolioReportNotFoundException("El informe solicitado no existe."));
    }

    private PortfolioReport readyReport(Long id) {
        var report = report(id);
        if (!"READY".equals(report.getStatus())) {
            throw new IllegalArgumentException("El informe debe estar listo antes de enviarlo.");
        }
        return report;
    }

    private String filename(PortfolioReport report, String extension) {
        return "informe_portafolio_" + report.getFrom() + "_" + report.getTo() + "." + extension;
    }

    private PortfolioReportListItem item(PortfolioReport report) {
        return new PortfolioReportListItem(
                report.getId(),
                report.getPortfolio().getId(),
                report.getPortfolioName(),
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
