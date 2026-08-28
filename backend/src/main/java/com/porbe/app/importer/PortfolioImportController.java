package com.porbe.app.importer;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/portfolio-import")
/** Recibe archivos de portafolio y ofrece la plantilla oficial de Excel. */
public class PortfolioImportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PortfolioImportService importService;

    public PortfolioImportController(PortfolioImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/template")
    ResponseEntity<Resource> template() throws IOException {
        var resource = new ClassPathResource("templates/plantilla_importacion_portafolio.xlsx");
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .contentLength(resource.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=plantilla_importacion_portafolio.xlsx")
                .body(resource);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PortfolioImportResult importPortfolio(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importService.importWorkbook(file, authentication.getName());
    }
}
