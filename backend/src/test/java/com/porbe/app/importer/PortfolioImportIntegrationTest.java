package com.porbe.app.importer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.portfolio.PortfolioRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
/** Verifica la importación atómica y sus principales reglas de validación. */
class PortfolioImportIntegrationTest {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioOperationRepository operationRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @BeforeEach
    void cleanDatabase() {
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void downloadsTheOfficialTemplate() throws Exception {
        mockMvc.perform(get("/api/portfolio-import/template")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=plantilla_importacion_portafolio.xlsx"));
    }

    @Test
    void importsValidOperationsAtomically() throws Exception {
        var file = workbookFile(validRows(), "portafolio-valido.xlsx");

        mockMvc.perform(multipart("/api/portfolio-import")
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.importedRows").value(5))
                .andExpect(jsonPath("$.errors").isEmpty());

        mockMvc.perform(get("/api/operations")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.operations.length()").value(5));
    }

    @Test
    void rejectsEveryRowWhenOneTotalIsInvalid() throws Exception {
        var rows = validRows();
        rows.set(0, new Object[] {
                "2025-01-06", "compra", "ECOPETROL.CL", "Ecopetrol", 100d, 1850d, 15000d, 199999d, "Total incorrecto"
        });

        mockMvc.perform(multipart("/api/portfolio-import")
                        .file(workbookFile(rows, "portafolio-invalido.xlsx"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("total del movimiento"));

        org.assertj.core.api.Assertions.assertThat(operationRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(importBatchRepository.count()).isZero();
    }

    @Test
    void preventsImportingTheSameFileTwice() throws Exception {
        var file = workbookFile(validRows(), "portafolio-duplicado.xlsx");

        mockMvc.perform(multipart("/api/portfolio-import")
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/portfolio-import")
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHIVO_DUPLICADO"));
    }

    private List<Object[]> validRows() {
        return new java.util.ArrayList<>(List.of(
                new Object[] {"2025-01-06", "compra", "ECOPETROL.CL", "Ecopetrol", 100d, 1850d, 15000d, 200000d, "Compra inicial"},
                new Object[] {"2025-02-10", "venta", "PFBCOLOM.CL", "Preferencial Bancolombia", 10d, 46000d, 9000d, 451000d, "Venta parcial"},
                new Object[] {"2025-04-03", "dividendo", "ECOPETROL.CL", "Ecopetrol", 100d, 89d, 0d, 8900d, "Dividendo"},
                new Object[] {"2025-01-03", "depósito", null, null, null, null, 0d, 2000000d, "Ingreso de efectivo"},
                new Object[] {"2025-06-02", "retiro", null, null, null, null, 0d, 250000d, "Retiro de efectivo"}));
    }

    private MockMultipartFile workbookFile(List<Object[]> rows, String filename) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Operaciones");
            var header = sheet.createRow(0);
            var headers = List.of(
                    "fecha",
                    "operación",
                    "ticker",
                    "nombre",
                    "cantidad",
                    "precio unitario",
                    "comisión",
                    "total del movimiento",
                    "notas");
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                var values = rows.get(rowIndex);
                for (int column = 0; column < values.length; column++) {
                    var value = values[column];
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(column).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", filename, XLSX_MEDIA_TYPE, output.toByteArray());
        }
    }
}
