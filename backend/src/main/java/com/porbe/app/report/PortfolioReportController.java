package com.porbe.app.report;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/** Expone generación, historial, programación y descarga de informes. */
@RestController
@RequestMapping("/api/reports")
public class PortfolioReportController {

    private final PortfolioReportService reportService;
    private final PortfolioReportScheduleService scheduleService;
    private final PortfolioReportAiNoteService aiNoteService;
    private final WhatsAppRecipientService recipientService;

    public PortfolioReportController(
            PortfolioReportService reportService,
            PortfolioReportScheduleService scheduleService,
            PortfolioReportAiNoteService aiNoteService,
            WhatsAppRecipientService recipientService) {
        this.reportService = reportService;
        this.scheduleService = scheduleService;
        this.aiNoteService = aiNoteService;
        this.recipientService = recipientService;
    }

    @GetMapping
    List<PortfolioReportListItem> list(@RequestParam(required = false) Long portfolioId) {
        return reportService.list(portfolioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PortfolioReportListItem generate(
            @Valid @RequestBody PortfolioReportRequest request,
            Principal principal) {
        return reportService.generate(
                request.portfolioId(), request.from(), request.to(), "MANUAL", principal.getName());
    }

    @GetMapping("/schedule")
    PortfolioReportScheduleResponse schedule() {
        return scheduleService.current();
    }

    @PutMapping("/schedule")
    PortfolioReportScheduleResponse updateSchedule(
            @Valid @RequestBody PortfolioReportScheduleRequest request,
            Principal principal) {
        return scheduleService.update(request, principal.getName());
    }

    @GetMapping("/whatsapp/status")
    WhatsAppConnectionStatus whatsAppStatus() {
        return reportService.whatsAppStatus();
    }

    @GetMapping("/whatsapp/recipients")
    List<WhatsAppRecipientResponse> whatsAppRecipients() {
        return recipientService.list();
    }

    @PostMapping("/whatsapp/recipients")
    @ResponseStatus(HttpStatus.CREATED)
    WhatsAppRecipientResponse createWhatsAppRecipient(
            @Valid @RequestBody WhatsAppRecipientRequest request,
            Principal principal) {
        return recipientService.create(request, principal.getName());
    }

    @PutMapping("/whatsapp/recipients/{id}")
    WhatsAppRecipientResponse updateWhatsAppRecipient(
            @PathVariable Long id,
            @Valid @RequestBody WhatsAppRecipientRequest request,
            Principal principal) {
        return recipientService.update(id, request, principal.getName());
    }

    @DeleteMapping("/whatsapp/recipients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWhatsAppRecipient(@PathVariable Long id) {
        recipientService.delete(id);
    }

    @PostMapping("/whatsapp/recipients/{id}/test")
    PortfolioReportListItem testWhatsAppRecipient(@PathVariable Long id) {
        return reportService.testDelivery(id);
    }

    @PostMapping("/{id}/whatsapp")
    PortfolioReportListItem sendByWhatsApp(
            @PathVariable Long id,
            @Valid @RequestBody WhatsAppReportDeliveryRequest request) {
        return reportService.deliver(id, request.recipientId());
    }

    @GetMapping("/{id}/image")
    ResponseEntity<byte[]> image(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download) {
        return file(reportService.image(id), download);
    }

    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return file(reportService.pdf(id), true);
    }

    @GetMapping("/ai-info")
    PortfolioReportAiSettingsResponse aiInfo() {
        return aiNoteService.info(scheduleService.aiSettings());
    }

    @PutMapping("/ai-info")
    PortfolioReportAiSettingsResponse updateAi(
            @Valid @RequestBody PortfolioReportAiSettingsRequest request,
            Principal principal) {
        return aiNoteService.info(scheduleService.updateAi(request, principal.getName()));
    }

    private ResponseEntity<byte[]> file(PortfolioReportFile file, boolean download) {
        var disposition = ContentDisposition.builder(download ? "attachment" : "inline")
                .filename(file.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(file.content().length)
                .body(file.content());
    }
}
