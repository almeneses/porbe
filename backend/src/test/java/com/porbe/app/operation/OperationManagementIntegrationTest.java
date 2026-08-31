package com.porbe.app.operation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.importer.ImportBatch;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.portfolio.PortfolioRepository;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
