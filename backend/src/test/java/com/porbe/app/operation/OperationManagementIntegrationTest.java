package com.porbe.app.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.importer.ImportBatch;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.importer.ImportSourceType;
import com.porbe.app.portfolio.PortfolioRepository;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica CRUD manual, consistencia, exportación, auditoría y reversión de lotes. */
@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class OperationManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioOperationRepository operationRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private OperationAuditRepository auditRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioService portfolioService;

    @BeforeEach
    void cleanDatabase() {
        auditRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void managesManualOperationsAndReportsTheNegativeMovement() throws Exception {
        mockMvc.perform(post("/api/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchase("10", "100", "5", "1005"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.quantityAfter").value(10));

        var purchaseId = operationRepository.findAll().getFirst().getId();
        mockMvc.perform(put("/api/operations/{id}", purchaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchase("12", "100", "5", "1205"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.updatedBy").value("admin"));

        mockMvc.perform(post("/api/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sale("15", "120", "0", "1800"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityAfter").value(-3))
                .andExpect(jsonPath("$.consistencyIssue").isNotEmpty());

        mockMvc.perform(get("/api/operations")
                        .param("ticker", "ECOPETROL")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.returned").value(2))
                .andExpect(jsonPath("$.operations[0].consistencyIssue").isNotEmpty());

        mockMvc.perform(get("/api/operations/export")
                        .param("ticker", "ECOPETROL.CL")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=operaciones_portafolio.xlsx"));

        var saleId = operationRepository.findAll().stream()
                .filter(operation -> operation.getType() == OperationType.VENTA)
                .findFirst()
                .orElseThrow()
                .getId();
        mockMvc.perform(delete("/api/operations/{id}", saleId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/operation-audit").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("DELETED"))
                .andExpect(jsonPath("$[1].action").value("CREATED"))
                .andExpect(jsonPath("$[2].action").value("UPDATED"));
    }

    @Test
    void rejectsInvalidManualDataAndRevertsAnImportedBatch() throws Exception {
        mockMvc.perform(post("/api/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchase("10", "100", "0", "999"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERACION_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("totalAmount"));

        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "lote-a-revertir.xlsx",
                "5500012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551",
                1,
                "admin"));
        operationRepository.save(new PortfolioOperation(
                portfolio,
                batch,
                LocalDate.of(2026, 1, 5),
                OperationType.DEPOSITO,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                "Aporte"));

        mockMvc.perform(delete("/api/operation-batches/{id}", batch.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(operationRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(importBatchRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(auditRepository.findAll())
                .extracting(OperationAudit::getAction)
                .containsExactly(OperationAuditAction.BATCH_REVERTED);
    }

    @Test
    void exportsTheWholeSelectedPortfolioAndReimportsEveryMovementField() throws Exception {
        var source = portfolioService.getOrCreateDefaultPortfolio();
        var target = portfolioService.getPortfolio(portfolioService.create("Copia del portafolio").id());
        var date = LocalDate.of(2025, 4, 3);
        var imported = importBatchRepository.save(new ImportBatch(source, "original.xlsx", "original", 3, "admin"));
        var manual = importBatchRepository.save(new ImportBatch(
                source, "Operación manual", "manual", 1002, "admin", ImportSourceType.MANUAL));
        operationRepository.saveAll(List.of(
                new PortfolioOperation(source, imported, date, OperationType.COMPRA, "ECOPETROL.CL", "Ecopetrol",
                        new BigDecimal("123456789.12345678"), new BigDecimal("0.01"), new BigDecimal("0.01"),
                        new BigDecimal("1234567.90"), "Compra con fracciones"),
                new PortfolioOperation(source, imported, date, OperationType.VENTA, "ECOPETROL.CL", "Ecopetrol",
                        BigDecimal.ONE, new BigDecimal("123456789.12345678"), new BigDecimal("0.02"),
                        new BigDecimal("123456789.10"), null),
                new PortfolioOperation(source, imported, date, OperationType.DIVIDENDO, "ECOPETROL.CL", "Ecopetrol",
                        null, null, new BigDecimal("12345678901234567890.12"), BigDecimal.ZERO, "Dividendo neto cero"),
                new PortfolioOperation(source, manual, date.minusDays(1), OperationType.DEPOSITO, null, null,
                        null, null, BigDecimal.ZERO, new BigDecimal("1234567890123456789012.34"), "Aporte exacto"),
                new PortfolioOperation(source, manual, date.plusDays(1), OperationType.RETIRO, null, null,
                        null, null, BigDecimal.ZERO, new BigDecimal("250.25"), "Retiro")));
        operationRepository.saveAll(java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> new PortfolioOperation(source, manual, date, OperationType.DEPOSITO,
                        null, null, null, null, BigDecimal.ZERO, BigDecimal.ONE, "Aporte " + index))
                .toList());
        var other = portfolioService.getPortfolio(portfolioService.create("Otro portafolio").id());
        var otherBatch = importBatchRepository.save(new ImportBatch(other, "otro.xlsx", "otro", 1, "admin"));
        operationRepository.save(new PortfolioOperation(other, otherBatch, date, OperationType.DEPOSITO,
                null, null, null, null, BigDecimal.ZERO, BigDecimal.TEN, "No debe exportarse"));

        mockMvc.perform(get("/api/operations").param("portfolioId", source.getId().toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.total").value(1005))
                .andExpect(jsonPath("$.returned").value(1000));
        var bytes = mockMvc.perform(get("/api/operations/export")
                        .param("portfolioId", source.getId().toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        mockMvc.perform(multipart("/api/portfolio-import")
                        .param("portfolioId", target.getId().toString())
                        .file(new MockMultipartFile("file", "operaciones_portafolio.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(1005));

        assertThat(operationRepository.findAllByPortfolioOrderByDateAscIdAsc(target))
                .usingRecursiveComparison()
                .ignoringFields("id", "portfolio", "importBatch", "createdAt", "updatedAt", "updatedBy")
                .isEqualTo(operationRepository.findAllByPortfolioOrderByDateAscIdAsc(source));
        assertThat(operationRepository.findAllByPortfolioOrderByDateAscIdAsc(other)).hasSize(1);
    }

    private String purchase(String quantity, String price, String commission, String total) {
        return operationJson("compra", quantity, price, commission, total);
    }

    private String sale(String quantity, String price, String commission, String total) {
        return operationJson("venta", quantity, price, commission, total);
    }

    private String operationJson(String type, String quantity, String price, String commission, String total) {
        return """
                {
                  "date": "2026-01-05",
                  "type": "%s",
                  "ticker": "ECOPETROL.CL",
                  "name": "Ecopetrol S.A.",
                  "quantity": %s,
                  "unitPrice": %s,
                  "commission": %s,
                  "totalAmount": %s,
                  "notes": "Registro manual"
                }
                """.formatted(type, quantity, price, commission, total);
    }
}
