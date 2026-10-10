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
        var template = new ClassPathResource("templates/plantilla_importacion_portafolio.xlsx");
        org.springframework.core.io.ByteArrayResource resource;
        try (var input = template.getInputStream();
                var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input);
                var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.getSheet("Operaciones");
            var currencyHeader = sheet.getRow(0).createCell(9);
            currencyHeader.setCellValue("moneda");
            currencyHeader.setCellStyle(sheet.getRow(0).getCell(8).getCellStyle());
            sheet.setColumnWidth(9, 12 * 256);
            for (var row : sheet) {
                if (row.getRowNum() > 0 && row.getCell(0) != null) row.createCell(9).setCellValue("COP");
            }
            for (var index = 0; index < sheet.getDataValidations().size(); index++) {
                var validation = sheet.getDataValidations().get(index);
                if (validation.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 1) {
                    sheet.getCTWorksheet().getDataValidations().getDataValidationArray(index)
                            .setFormula1("\"compra,venta,dividendo,depósito,retiro,compra USD,venta USD\"");
                }
            }
            var helper = sheet.getDataValidationHelper();
            sheet.addValidationData(helper.createValidation(helper.createExplicitListConstraint(new String[]{"COP", "USD"}),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 5000, 9, 9)));
            workbook.write(output);
            resource = new org.springframework.core.io.ByteArrayResource(output.toByteArray());
        }
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
            @RequestParam(required = false) Long portfolioId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importService.importWorkbook(portfolioId, file, authentication.getName());
    }
}
