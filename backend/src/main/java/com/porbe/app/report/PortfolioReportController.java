package com.porbe.app.report;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PortfolioReportController(
            PortfolioReportService reportService,
            PortfolioReportScheduleService scheduleService) {
        this.reportService = reportService;
        this.scheduleService = scheduleService;
    }

    @GetMapping
    List<PortfolioReportListItem> list() {
        return reportService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PortfolioReportListItem generate(
            @Valid @RequestBody PortfolioReportRequest request,
            Principal principal) {
        return reportService.generate(request.from(), request.to(), "MANUAL", principal.getName());
    }

    @GetMapping("/schedule")
    PortfolioReportScheduleResponse schedule() {
        return scheduleService.current();
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
