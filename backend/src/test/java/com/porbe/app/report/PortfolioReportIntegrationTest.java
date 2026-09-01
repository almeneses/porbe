package com.porbe.app.report;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.porbe.app.PorbeApplication;
import com.porbe.app.importer.ImportBatch;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.market.DailyMarketBar;
import com.porbe.app.market.MarketDataPersistenceService;
import com.porbe.app.market.MarketDataSeries;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
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
import org.springframework.test.web.servlet.MockMvc;

/** Verifica generación, persistencia y descarga real de PNG y PDF. */
@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class PortfolioReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PortfolioReportRepository reportRepository;
    @Autowired private PortfolioOperationRepository operationRepository;
    @Autowired private ImportBatchRepository importBatchRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private MarketPriceDailyRepository priceRepository;
    @Autowired private MarketInstrumentRepository instrumentRepository;
    @Autowired private PortfolioService portfolioService;
    @Autowired private MarketDataPersistenceService marketDataPersistenceService;

    @BeforeEach
    void cleanDatabase() {
        reportRepository.deleteAll();
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void generatesAndDownloadsBothReportFormats() throws Exception {
        var context = operationContext();
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "2000");
        save(context, LocalDate.of(2026, 1, 5), OperationType.COMPRA, "ECOPETROL.CL", "10", "100", "1000");
        save(context, LocalDate.of(2026, 1, 12), OperationType.DIVIDENDO, "ECOPETROL.CL", null, null, "20");
        persistPrices();

        var response = mockMvc.perform(post("/api/reports")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2026-01-05\",\"to\":\"2026-01-16\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.portfolioName").value("Portafolio principal"))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.deliveryStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.imageSize", greaterThan(10000)))
                .andExpect(jsonPath("$.pdfSize", greaterThan(10000)))
                .andReturn().getResponse().getContentAsString();
        var id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/reports/{id}/image", id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertArrayEquals(
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
                        java.util.Arrays.copyOf(result.getResponse().getContentAsByteArray(), 4)));

        mockMvc.perform(get("/api/reports/{id}/pdf", id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        "%PDF",
                        new String(result.getResponse().getContentAsByteArray(), 0, 4)));

        mockMvc.perform(get("/api/reports").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void exposesTheFridayScheduleWithoutConfiguringDelivery() throws Exception {
        mockMvc.perform(get("/api/reports/schedule").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.runTime").value("17:30"))
                .andExpect(jsonPath("$.timezone").value("America/Bogota"))
                .andExpect(jsonPath("$.deliveryConfigured").value(false));
    }

    private OperationContext operationContext() {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "informe-test.xlsx",
                "f298d9f08da45cb89e98eef59f450b4b32bf0eb65bf737181193e236fee815cf",
                3,
                "admin"));
        return new OperationContext(portfolio, batch);
    }

    private void save(
            OperationContext context,
            LocalDate date,
            OperationType type,
            String ticker,
            String quantity,
            String unitPrice,
            String total) {
        operationRepository.save(new PortfolioOperation(
                context.portfolio(), context.batch(), date, type, ticker,
                ticker == null ? null : "Ecopetrol", decimal(quantity), decimal(unitPrice),
                BigDecimal.ZERO, decimal(total), null));
    }

    private void persistPrices() {
        marketDataPersistenceService.save(new MarketDataSeries(
                "ECOPETROL.CL", "Ecopetrol S.A.", "COP", "BVC", "EQUITY", "America/Bogota",
                new BigDecimal("120"),
                List.of(close(LocalDate.of(2026, 1, 2), "100"),
                        close(LocalDate.of(2026, 1, 9), "110"),
                        close(LocalDate.of(2026, 1, 16), "120"))),
                "TEST");
    }

    private DailyMarketBar close(LocalDate date, String amount) {
        var value = new BigDecimal(amount);
        return new DailyMarketBar(date, value, value, value, value, value, 1000L, true);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private record OperationContext(
            com.porbe.app.portfolio.Portfolio portfolio,
            ImportBatch batch) {
    }
}
