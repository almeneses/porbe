package com.porbe.app.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.porbe.app.report.PortfolioReportCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica costo promedio, ganancias y alertas de la valoración actual. */
@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class PortfolioValuationIntegrationTest {

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

    @Autowired
    private PortfolioHistoryService historyService;

    @Autowired
    private PortfolioReportCalculator reportCalculator;

    @BeforeEach
    void cleanDatabase() {
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void calculatesWeightedAverageAndPortfolioSummary() throws Exception {
        var context = operationContext("valoracion-test.xlsx", "1100012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551");
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "0", "1000000");
        save(context, LocalDate.of(2026, 1, 3), OperationType.COMPRA, "ECOPETROL.CL", "100", "1000", "1000", "101000");
        save(context, LocalDate.of(2026, 2, 3), OperationType.COMPRA, "ECOPETROL.CL", "50", "1200", "0", "60000");
        save(context, LocalDate.of(2026, 3, 3), OperationType.VENTA, "ECOPETROL.CL", "30", "1500", "1000", "44000");
        save(context, LocalDate.of(2026, 4, 3), OperationType.DIVIDENDO, "ECOPETROL.CL", null, null, "0", "10000");
        persistClose("1400");

        mockMvc.perform(get("/api/portfolio/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationCount").value(5))
                .andExpect(jsonPath("$.openPositionCount").value(1))
                .andExpect(jsonPath("$.marketValue").value(168000))
                .andExpect(jsonPath("$.costBasis").value(128800))
                .andExpect(jsonPath("$.cashBalance").value(893000))
                .andExpect(jsonPath("$.portfolioValue").value(1061000))
                .andExpect(jsonPath("$.netContributions").value(1000000))
                .andExpect(jsonPath("$.dividends").value(10000))
                .andExpect(jsonPath("$.realizedGain").value(11800))
                .andExpect(jsonPath("$.unrealizedGain").value(39200))
                .andExpect(jsonPath("$.totalGain").value(61000))
                .andExpect(jsonPath("$.returnRate").value(0.37888199))
                .andExpect(jsonPath("$.valuationComplete").value(true))
                .andExpect(jsonPath("$.positions[0].ticker").value("ECOPETROL.CL"))
                .andExpect(jsonPath("$.positions[0].quantity").value(120))
                .andExpect(jsonPath("$.positions[0].averageCost").value(1073.33333333))
                .andExpect(jsonPath("$.positions[0].lastPrice").value(1400))
                .andExpect(jsonPath("$.positions[0].totalGain").value(61000));
    }

    @Test
    void preservesFractionalSaleRoundingAndHistoricalPriceCutoffs() throws Exception {
        var context = operationContext("fracciones.xlsx", "5500012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551");
        save(context, LocalDate.of(2026, 1, 2), OperationType.DEPOSITO, null, null, null, "0", "10");
        for (var ticker : List.of("AAA", "BBB")) {
            save(context, LocalDate.of(2026, 1, 5), OperationType.COMPRA, ticker, "3", "0.33333333", "0", "1");
            save(context, LocalDate.of(2026, 1, 6), OperationType.VENTA, ticker, "1", "0.34", "0", "0.34");
            var close = new BigDecimal("0.335");
            marketDataPersistenceService.save(new MarketDataSeries(
                    ticker, ticker, "COP", "BVC", "EQUITY", "America/Bogota", close,
                    List.of(new DailyMarketBar(LocalDate.of(2026, 1, 9),
                            close, close, close, close, close, 100L, false))), "TEST");
        }
        save(context, LocalDate.of(2026, 1, 7), OperationType.DIVIDENDO, "AAA", null, null, "0", "0.01");
        save(context, LocalDate.of(2026, 1, 8), OperationType.RETIRO, null, null, null, "0", "1");

        mockMvc.perform(get("/api/portfolio/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedGain").value(0.02))
                .andExpect(jsonPath("$.totalGain").value(0.03))
                .andExpect(jsonPath("$.portfolioValue").value(9.03))
                .andExpect(jsonPath("$.netContributions").value(9))
                .andExpect(jsonPath("$.positions[0].provisionalPrice").value(true));

        var futureClose = BigDecimal.TEN;
        marketDataPersistenceService.save(new MarketDataSeries(
                "AAA", "AAA", "COP", "BVC", "EQUITY", "America/Bogota", futureClose,
                List.of(new DailyMarketBar(LocalDate.of(2026, 1, 16),
                        futureClose, futureClose, futureClose, futureClose, futureClose, 100L, true))), "TEST");
        var from = LocalDate.of(2026, 1, 5);
        var to = LocalDate.of(2026, 1, 9);
        var week = historyService.weeklyHistory(context.portfolio().getId(), from, to).weeks().getLast();
        assertThat(week.realizedGain()).isEqualByComparingTo("0.01");
        assertThat(week.totalGain()).isEqualByComparingTo("0.02");
        assertThat(week.portfolioValue()).isEqualByComparingTo("9.03");
        assertThat(week.positions().getFirst().closePrice()).isEqualByComparingTo("0.335");
        assertThat(week.periodReturn()).isNull();
        assertThat(week.positions()).allMatch(PortfolioWeeklyPositionResponse::provisionalPrice);
        assertThatThrownBy(() -> reportCalculator.calculate(context.portfolio().getId(), from, to))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precios de cierre");

        // El reporte requiere cierres definitivos al retirar efectivo y al terminar la semana.
        for (var ticker : List.of("AAA", "BBB")) {
            var close = new BigDecimal("0.335");
            marketDataPersistenceService.save(new MarketDataSeries(
                    ticker, ticker, "COP", "BVC", "EQUITY", "America/Bogota", close,
                    List.of(new DailyMarketBar(LocalDate.of(2026, 1, 8),
                                    close, close, close, close, close, 100L, true),
                            new DailyMarketBar(to, close, close, close, close, close, 100L, true))), "TEST");
        }
        var report = reportCalculator.calculate(context.portfolio().getId(), from, to);
        assertThat(report.accumulatedGain()).isEqualByComparingTo(week.totalGain());
        assertThat(report.portfolioValue()).isEqualByComparingTo(week.portfolioValue());
        assertThat(report.provisionalPrices()).isZero();
        assertThat(report.periodGain()).isEqualByComparingTo("0.03");
        assertThat(report.periodReturn()).isEqualByComparingTo("0.003");
    }

    @Test
    void reportsMissingPricesAsPartialValuation() throws Exception {
        var context = operationContext("sin-precio.xlsx", "2200012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551");
        save(context, LocalDate.of(2026, 1, 3), OperationType.COMPRA, "ECOPETROL.CL", "10", "1000", "0", "10000");

        mockMvc.perform(get("/api/portfolio/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(false))
                .andExpect(jsonPath("$.unpricedPositions").value(1))
                .andExpect(jsonPath("$.positions[0].valued").value(false))
                .andExpect(jsonPath("$.positions[0].marketValue").doesNotExist());
    }

    @Test
    void reportsASaleThatExceedsTheAvailablePosition() throws Exception {
        var context = operationContext("sobreventa.xlsx", "3300012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551");
        save(context, LocalDate.of(2026, 1, 3), OperationType.COMPRA, "ECOPETROL.CL", "10", "1000", "0", "10000");
        save(context, LocalDate.of(2026, 1, 4), OperationType.VENTA, "ECOPETROL.CL", "15", "1200", "0", "18000");

        mockMvc.perform(get("/api/portfolio/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationComplete").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("VENTA_SIN_POSICION"))
                .andExpect(jsonPath("$.positions[0].calculationComplete").value(false));
    }

    private OperationContext operationContext(String filename, String hash) {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(portfolio, filename, hash, 5, "admin"));
        return new OperationContext(portfolio, batch);
    }

    private void save(
            OperationContext context,
            LocalDate date,
            OperationType type,
            String ticker,
            String quantity,
            String unitPrice,
            String commission,
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
                decimal(commission),
                decimal(total),
                null));
    }

    private void persistClose(String close) {
        var value = new BigDecimal(close);
        marketDataPersistenceService.save(new MarketDataSeries(
                "ECOPETROL.CL",
                "Ecopetrol S.A.",
                "COP",
                "BVC",
                "EQUITY",
                "America/Bogota",
                value,
                List.of(new DailyMarketBar(
                        LocalDate.of(2026, 8, 27),
                        value,
                        value,
                        value,
                        value,
                        value,
                        1000L,
                        true))), "TEST");
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** Entidades compartidas por las operaciones de un escenario de prueba. */
    private record OperationContext(Portfolio portfolio, ImportBatch batch) {
    }
}
