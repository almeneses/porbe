package com.porbe.app.operation;

import com.porbe.app.importer.ImportSourceType;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

/** Expone consulta, exportación y mantenimiento manual del libro de operaciones. */
@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OperationManagementService managementService;
    private final OperationExportService exportService;

    public OperationController(
            OperationManagementService managementService,
            OperationExportService exportService) {
        this.managementService = managementService;
        this.exportService = exportService;
    }

    @GetMapping
    OperationsResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long importBatchId) {
        return managementService.list(filter(from, to, ticker, type, sourceType, importBatchId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OperationResponse create(@RequestBody OperationRequest request, Authentication authentication) {
        return managementService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    OperationResponse update(
            @PathVariable Long id,
            @RequestBody OperationRequest request,
            Authentication authentication) {
        return managementService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, Authentication authentication) {
        managementService.delete(id, authentication.getName());
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long importBatchId) {
        var content = exportService.export(managementService.filteredOperations(
                filter(from, to, ticker, type, sourceType, importBatchId)));
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operaciones_portafolio.xlsx")
                .body(content);
    }

    private OperationFilter filter(
            LocalDate from,
            LocalDate to,
            String ticker,
            String type,
            String sourceType,
            Long importBatchId) {
        var parsedType = type == null || type.isBlank()
                ? null
                : OperationType.fromSpreadsheet(type)
                        .orElseThrow(() -> new IllegalArgumentException("El tipo de operación no es válido."));
        ImportSourceType parsedSource = null;
        if (sourceType != null && !sourceType.isBlank()) {
            try {
                parsedSource = ImportSourceType.valueOf(sourceType.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("El origen de la operación no es válido.");
            }
        }
        return new OperationFilter(from, to, ticker, parsedType, parsedSource, importBatchId);
    }
}
