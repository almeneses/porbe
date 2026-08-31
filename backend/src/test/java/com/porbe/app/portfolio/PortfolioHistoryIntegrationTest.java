package com.porbe.app.portfolio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica la reconstrucción semanal, la variación y el detalle por ticker. */
@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class PortfolioHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioOperationRepository operationRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private MarketPriceDailyRepository priceRepository;

    @Autowired
    private MarketInstrumentRepository instrumentRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private MarketDataPersistenceService marketDataPersistenceService;

    @BeforeEach
    void cleanDatabase() {
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void calculatesWeeklyPortfolioAndUsesTheLastTradingClose() throws Exception {
        var context = operationContext();
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "2000");
        save(context, LocalDate.of(2026, 1, 5), OperationType.COMPRA, "ECOPETROL.CL", "10", "100", "1000");
        save(context, LocalDate.of(2026, 1, 12), OperationType.DIVIDENDO, "ECOPETROL.CL", null, null, "20");
        persistPrices();

        mockMvc.perform(get("/api/portfolio/history/weekly")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-16")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekCount").value(3))
                .andExpect(jsonPath("$.valuationComplete").value(true))
                .andExpect(jsonPath("$.weeks[0].weekEnding").value("2026-01-02"))
                .andExpect(jsonPath("$.weeks[0].portfolioValue").value(2000))
                .andExpect(jsonPath("$.weeks[0].externalCashFlow").value(2000))
                .andExpect(jsonPath("$.weeks[0].periodReturn").value(0))
                .andExpect(jsonPath("$.weeks[0].timeWeightedReturn").value(0))
                .andExpect(jsonPath("$.weeks[0].nominalVariation").doesNotExist())
                .andExpect(jsonPath("$.weeks[1].marketValue").value(1100))
                .andExpect(jsonPath("$.weeks[1].investedCapital").value(1000))
                .andExpect(jsonPath("$.weeks[1].cashBalance").value(1000))
                .andExpect(jsonPath("$.weeks[1].portfolioValue").value(2100))
                .andExpect(jsonPath("$.weeks[1].totalGain").value(100))
                .andExpect(jsonPath("$.weeks[1].nominalVariation").value(100))
                .andExpect(jsonPath("$.weeks[1].percentageVariation").value(0.05))
                .andExpect(jsonPath("$.weeks[1].periodReturn").value(0.05))
                .andExpect(jsonPath("$.weeks[1].timeWeightedReturn").value(0.05))
                .andExpect(jsonPath("$.weeks[2].portfolioValue").value(2220))
                .andExpect(jsonPath("$.weeks[2].dividends").value(20))
                .andExpect(jsonPath("$.weeks[2].totalGain").value(220))
                .andExpect(jsonPath("$.weeks[2].nominalVariation").value(120))
                .andExpect(jsonPath("$.weeks[2].percentageVariation").value(0.05714286))
                .andExpect(jsonPath("$.weeks[2].externalCashFlow").value(0))
                .andExpect(jsonPath("$.weeks[2].timeWeightedReturn").value(0.11))
                .andExpect(jsonPath("$.weeks[2].annualizedReturn").exists())
                .andExpect(jsonPath("$.weeks[2].positions[0].ticker").value("ECOPETROL.CL"))
                .andExpect(jsonPath("$.weeks[2].positions[0].quantity").value(10))
                .andExpect(jsonPath("$.weeks[2].positions[0].closePrice").value(120))
                .andExpect(jsonPath("$.weeks[2].positions[0].priceDate").value("2026-01-15"))
                .andExpect(jsonPath("$.weeks[2].positions[0].marketValue").value(1200));
    }

    @Test
    void marksAWeekAsPartialWhenAnOpenPositionHasNoPrice() throws Exception {
        var context = operationContext();
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "1000");
        save(context, LocalDate.of(2026, 1, 5), OperationType.COMPRA, "ECOPETROL.CL", "10", "100", "1000");

        mockMvc.perform(get("/api/portfolio/history/weekly")
                        .param("from", "2026-01-09")
                        .param("to", "2026-01-09")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(false))
                .andExpect(jsonPath("$.weeks[0].unpricedPositions").value(1))
                .andExpect(jsonPath("$.weeks[0].positions[0].valued").value(false))
                .andExpect(jsonPath("$.weeks[0].positions[0].marketValue").doesNotExist());
    }

    @Test
    void keepsForeignCurrencyPositionsOutsideTheCopConsolidation() throws Exception {
        var context = operationContext();
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "1000");
        save(context, LocalDate.of(2026, 1, 5), OperationType.COMPRA, "AAPL", "1", "200", "200");
        marketDataPersistenceService.save(new MarketDataSeries(
                "AAPL",
                "Apple Inc.",
                "USD",
                "NMS",
                "EQUITY",
                "America/New_York",
                new BigDecimal("210"),
                List.of(close(LocalDate.of(2026, 1, 9), "210"))),
                "TEST");

        mockMvc.perform(get("/api/portfolio/history/weekly")
                        .param("from", "2026-01-09")
                        .param("to", "2026-01-09")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(false))
                .andExpect(jsonPath("$.weeks[0].portfolioValue").value(1000))
                .andExpect(jsonPath("$.weeks[0].foreignCurrencyPositions").value(1))
                .andExpect(jsonPath("$.weeks[0].positions[0].currency").value("USD"))
                .andExpect(jsonPath("$.weeks[0].positions[0].marketValue").value(210))
                .andExpect(jsonPath("$.weeks[0].positions[0].foreignCurrency").value(true));
    }

    @Test
    void rejectsAnInvertedDateRange() throws Exception {
        mockMvc.perform(get("/api/portfolio/history/weekly")
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-01")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("La fecha inicial no puede ser posterior a la fecha final."));
    }

    private OperationContext operationContext() {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "historico-test.xlsx",
                "4400012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551",
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
                context.portfolio(),
                context.batch(),
                date,
                type,
                ticker,
                ticker == null ? null : "Ecopetrol",
                decimal(quantity),
                decimal(unitPrice),
                BigDecimal.ZERO,
                decimal(total),
                null));
    }

    private void persistPrices() {
        marketDataPersistenceService.save(new MarketDataSeries(
                "ECOPETROL.CL",
                "Ecopetrol S.A.",
                "COP",
                "BVC",
                "EQUITY",
                "America/Bogota",
                new BigDecimal("120"),
                List.of(
                        close(LocalDate.of(2026, 1, 9), "110"),
                        close(LocalDate.of(2026, 1, 15), "120"))),
                "TEST");
    }

    private DailyMarketBar close(LocalDate date, String amount) {
        var value = new BigDecimal(amount);
        return new DailyMarketBar(date, value, value, value, value, value, 1000L, true);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** Entidades compartidas por las operaciones del escenario histórico. */
    private record OperationContext(Portfolio portfolio, ImportBatch batch) {
    }
}
