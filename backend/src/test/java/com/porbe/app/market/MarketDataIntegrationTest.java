package com.porbe.app.market;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.importer.ImportBatch;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.portfolio.PortfolioRepository;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
/** Verifica endpoints de mercado con un proveedor controlado de pruebas. */
class MarketDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketPriceDailyRepository priceRepository;

    @Autowired
    private MarketInstrumentRepository instrumentRepository;

    @Autowired
    private MarketDataScheduleRepository scheduleRepository;

    @Autowired
    private PortfolioOperationRepository operationRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioService portfolioService;

    @MockitoBean
    private YahooFinanceMarketDataClient provider;

    @MockitoBean
    private StockAnalysisMarketDataClient stockAnalysis;

    @Autowired
    private MarketDataSyncService syncService;

    @Autowired
    private MarketDataPersistenceService persistenceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
        given(provider.source()).willReturn("YAHOO_FINANCE");
        given(stockAnalysis.source()).willReturn("STOCK_ANALYSIS");
        scheduleRepository.findByScheduleKey(MarketDataSchedule.PORTFOLIO_CLOSES).ifPresent(schedule -> {
            schedule.update(false, DayOfWeek.SATURDAY, LocalTime.of(8, 0), "test");
            scheduleRepository.save(schedule);
        });
    }

    @Test
    void synchronizesAndReturnsDailyPortfolioPrices() throws Exception {
        createPurchase();
        given(provider.source()).willReturn("YAHOO_FINANCE");
        given(provider.fetchDaily(eq("ECOPETROL.CL"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(series());

        mockMvc.perform(post("/api/market-data/sync")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickers").value(1))
                .andExpect(jsonPath("$.successfulTickers").value(1))
                .andExpect(jsonPath("$.storedPrices").value(2))
                .andExpect(jsonPath("$.results[0].ticker").value("ECOPETROL.CL"));
        verify(provider).fetchDaily(
                eq("ECOPETROL.CL"),
                eq(LocalDate.of(2024, 1, 19)),
                any(LocalDate.class));

        mockMvc.perform(get("/api/market-data")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("YAHOO_FINANCE"))
                .andExpect(jsonPath("$.tickers[0].currency").value("COP"))
                .andExpect(jsonPath("$.tickers[0].storedDays").value(2))
                .andExpect(jsonPath("$.tickers[0].lastClose").value(2605));

        mockMvc.perform(get("/api/market-data/ECOPETROL.CL/daily")
                        .param("from", "2026-08-24")
                        .param("to", "2026-08-26")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("ECOPETROL.CL"))
                .andExpect(jsonPath("$.prices.length()").value(2))
                .andExpect(jsonPath("$.prices[1].finalClose").value(true));

        mockMvc.perform(post("/api/market-data/sync")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        verify(provider).fetchDaily(
                eq("ECOPETROL.CL"),
                eq(LocalDate.of(2026, 8, 25)),
                any(LocalDate.class));
        org.assertj.core.api.Assertions.assertThat(priceRepository.count()).isEqualTo(2);
    }

    @Test
    void switchesNucoToStockAnalysisRepairsAvailableDatesAndKeepsOlderHistory() {
        createPurchase();
        createPurchase("NUCO.CL");
        var instrument = instrumentRepository.save(new MarketInstrument("NUCO.CL"));
        var older = nucoBar(LocalDate.of(2024, 1, 19), "51820");
        var latest = nucoBar(LocalDate.of(2026, 8, 25), "51820");
        priceRepository.saveAll(List.of(
                new MarketPriceDaily(instrument, older, "YAHOO_FINANCE", OffsetDateTime.now()),
                new MarketPriceDaily(instrument, latest, "YAHOO_FINANCE", OffsetDateTime.now())));
        given(provider.fetchDaily(eq("ECOPETROL.CL"), any(LocalDate.class), any(LocalDate.class))).willReturn(series());
        var replacement = new MarketDataSeries("NUCO.CL", "Nu Holdings Ltd.", "COP", "BVC", "EQUITY",
                "America/Bogota", new BigDecimal("49500"),
                List.of(nucoBar(LocalDate.of(2026, 8, 25), "49500")));
        given(stockAnalysis.fetchDaily(eq("NUCO.CL"), any(LocalDate.class), any(LocalDate.class))).willReturn(replacement);

        var result = syncService.syncPortfolio();
        assertThat(result.successfulTickers()).isEqualTo(2);
        assertThat(result.results()).filteredOn(item -> item.ticker().equals("NUCO.CL"))
                .extracting(TickerSyncResult::message).allMatch(message -> message.contains("solo cubre desde 2026-08-25"));
        verify(stockAnalysis).fetchDaily(eq("NUCO.CL"), eq(MarketDataSyncService.MARKET_HISTORY_START), any(LocalDate.class));
        verify(provider, never()).fetchDaily(eq("NUCO.CL"), any(LocalDate.class), any(LocalDate.class));
        var prices = syncService.prices("NUCO.CL", LocalDate.of(2024, 1, 19), LocalDate.of(2026, 8, 25)).prices();
        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst().close()).isEqualByComparingTo("51820");
        assertThat(prices.getLast().close()).isEqualByComparingTo("49500");
        var status = syncService.status(portfolioService.getOrCreateDefaultPortfolio().getId());
        assertThat(status.source()).contains("STOCK_ANALYSIS", "YAHOO_FINANCE");
        assertThat(status.tickers()).filteredOn(item -> item.ticker().equals("NUCO.CL"))
                .extracting(MarketTickerStatus::source).containsExactly("STOCK_ANALYSIS");

        syncService.syncPortfolio();
        verify(stockAnalysis).fetchDaily(eq("NUCO.CL"), eq(LocalDate.of(2026, 8, 25)), any(LocalDate.class));
        assertThat(priceRepository.countByInstrument(instrument)).isEqualTo(2);
    }

    @Test
    void keepsNucoPricesOnScrapingFailureAndStillUpdatesOtherTickers() {
        createPurchase();
        createPurchase("NUCO.CL");
        var instrument = instrumentRepository.save(new MarketInstrument("NUCO.CL"));
        priceRepository.save(new MarketPriceDaily(instrument, nucoBar(LocalDate.of(2026, 8, 25), "49500"),
                "STOCK_ANALYSIS", OffsetDateTime.now()));
        given(stockAnalysis.fetchDaily(eq("NUCO.CL"), any(LocalDate.class), any(LocalDate.class)))
                .willThrow(new MarketDataProviderException("Stock Analysis no respondió."));
        given(provider.fetchDaily(eq("ECOPETROL.CL"), any(LocalDate.class), any(LocalDate.class))).willReturn(series());

        var result = syncService.syncPortfolio();
        assertThat(result.successfulTickers()).isEqualTo(1);
        assertThat(result.results()).filteredOn(item -> item.ticker().equals("NUCO.CL"))
                .extracting(TickerSyncResult::success).containsExactly(false);
        assertThat(syncService.prices("NUCO.CL", LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 25))
                .prices().getFirst().close()).isEqualByComparingTo("49500");
        verify(provider, never()).fetchDaily(eq("NUCO.CL"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void keepsTheUsNuTickerOnYahoo() {
        createPurchase("NU");
        var usSeries = new MarketDataSeries("NU", "Nu Holdings Ltd.", "USD", "NYSE", "EQUITY",
                "America/New_York", new BigDecimal("14"), List.of(nucoBar(LocalDate.of(2026, 8, 25), "14")));
        given(provider.fetchDaily(eq("NU"), any(LocalDate.class), any(LocalDate.class))).willReturn(usSeries);
        assertThat(syncService.syncPortfolio().successfulTickers()).isEqualTo(1);
        verify(provider).fetchDaily(eq("NU"), any(LocalDate.class), any(LocalDate.class));
        verify(stockAnalysis, never()).fetchDaily(any(), any(), any());
    }

    @Test
    void configuresAutomaticUpdatesSectorsAndWeeklyCloseHistory() throws Exception {
        createPurchase();
        given(provider.source()).willReturn("YAHOO_FINANCE");
        given(provider.fetchDaily(eq("ECOPETROL.CL"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(series());

        mockMvc.perform(post("/api/market-data/sync")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/market-data/ECOPETROL.CL/sector")
                        .contentType("application/json")
                        .content("{\"sector\":\"Petróleo y gas\"}")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector").value("Petróleo y gas"));

        mockMvc.perform(get("/api/market-data/weekly-closes")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2024-01-19"))
                .andExpect(jsonPath("$.tickerCount").value(1))
                .andExpect(jsonPath("$.weekCount").isNumber())
                .andExpect(jsonPath("$.tickers[0].ticker").value("ECOPETROL.CL"))
                .andExpect(jsonPath("$.tickers[0].sector").value("Petróleo y gas"))
                .andExpect(jsonPath("$.tickers[0].closes[0].weekEnding").value("2024-01-19"));

        var createdAt = scheduleRepository.findByScheduleKey(MarketDataSchedule.PORTFOLIO_CLOSES)
                .orElseThrow().getCreatedAt();
        assertThat(createdAt).isNotNull();
        mockMvc.perform(put("/api/market-data/schedule")
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"dayOfWeek\":\"FRIDAY\",\"runTime\":\"19:30\"}")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.runTime").value("19:30:00"))
                .andExpect(jsonPath("$.timezone").value("America/Bogota"))
                .andExpect(jsonPath("$.nextRunAt").exists());
        var saved = scheduleRepository.findByScheduleKey(MarketDataSchedule.PORTFOLIO_CLOSES).orElseThrow();
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void returnsAnEmptySyncWhenPortfolioHasNoTickers() throws Exception {
        given(provider.source()).willReturn("YAHOO_FINANCE");

        mockMvc.perform(post("/api/market-data/sync")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickers").value(0))
                .andExpect(jsonPath("$.storedPrices").value(0));
    }

    @Test
    void syncsDollarCashWithoutStocksAndBackfillsBeforePreviouslyStoredHistory() {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(portfolio, "usd", "usd-hash", 1, "admin"));
        var firstDate = LocalDate.of(2023, 1, 7);
        operationRepository.save(new PortfolioOperation(portfolio, batch, firstDate, OperationType.COMPRA_USD,
                null, null, new BigDecimal("100"), new BigDecimal("4000"), BigDecimal.ZERO, new BigDecimal("400000"), null));
        var instrument = instrumentRepository.save(new MarketInstrument("COP=X"));
        var latest = LocalDate.of(2026, 1, 9);
        priceRepository.save(new MarketPriceDaily(instrument, nucoBar(latest, "4200"), "YAHOO_FINANCE", OffsetDateTime.now()));
        var resultSeries = new MarketDataSeries("COP=X", "USD/COP", "COP", "CCY", "CURRENCY", "Europe/London",
                new BigDecimal("4200"), List.of(nucoBar(firstDate.minusDays(1), "4000"), nucoBar(latest, "4200")));
        given(provider.fetchDaily(eq("COP=X"), any(LocalDate.class), any(LocalDate.class))).willReturn(resultSeries);
        var response = syncService.syncPortfolio(portfolio.getId());
        assertThat(response.totalTickers()).isEqualTo(1);
        assertThat(response.successfulTickers()).isEqualTo(1);
        verify(provider).fetchDaily(eq("COP=X"), eq(firstDate.minusDays(7)), any(LocalDate.class));
        assertThat(syncService.status(portfolio.getId()).tickers()).extracting(MarketTickerStatus::ticker).containsExactly("COP=X");
        syncService.syncPortfolio(portfolio.getId());
        verify(provider).fetchDaily(eq("COP=X"), eq(latest), any(LocalDate.class));
    }

    @Test
    void savesRepeatedDollarDatesAndUpdatesThemOnSubsequentRefreshes() {
        var earlier = LocalDate.of(2026, 10, 15);
        var latest = earlier.plusDays(1);
        var series = new MarketDataSeries("COP=X", "USD/COP", "COP", "CCY", "CURRENCY", "Europe/London",
                new BigDecimal("4200"), List.of(nucoBar(latest, "4100"), nucoBar(earlier, "4000"), nucoBar(latest, "4200")));

        assertThat(persistenceService.save(series, "YAHOO_FINANCE")).isEqualTo(2);
        var refreshed = new MarketDataSeries("COP=X", "USD/COP", "COP", "CCY", "CURRENCY", "Europe/London",
                new BigDecimal("4300"), List.of(nucoBar(latest, "4250"), nucoBar(earlier, "4050"),
                        new DailyMarketBar(latest, null, new BigDecimal("4400"), new BigDecimal("4200"),
                                new BigDecimal("4300"), new BigDecimal("4250"), null, false)));
        assertThat(persistenceService.save(refreshed, "UPDATED_SOURCE")).isEqualTo(2);
        assertThat(persistenceService.save(refreshed, "UPDATED_SOURCE")).isEqualTo(2);

        var prices = syncService.prices("COP=X", earlier, latest).prices();
        assertThat(priceRepository.count()).isEqualTo(2);
        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst().close()).isEqualByComparingTo("4050");
        assertThat(prices.getLast().close()).isEqualByComparingTo("4300");
        assertThat(prices.getLast().open()).isNull();
        assertThat(prices.getLast().high()).isEqualByComparingTo("4400");
        assertThat(prices.getLast().low()).isEqualByComparingTo("4200");
        assertThat(prices.getLast().adjustedClose()).isEqualByComparingTo("4250");
        assertThat(prices.getLast().volume()).isNull();
        assertThat(prices.getLast().finalClose()).isFalse();
        var instrument = instrumentRepository.findByTicker("COP=X").orElseThrow();
        assertThat(priceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument).orElseThrow().getSource())
                .isEqualTo("UPDATED_SOURCE");
    }

    @Test
    // H2 traduce ON CONFLICT a MERGE, que no ofrece la misma garantía de concurrencia.
    @EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql:.*")
    void upsertsTheSameNewDollarDateFromConcurrentTransactions() throws Exception {
        var instrument = instrumentRepository.save(new MarketInstrument("COP=X"));
        var date = LocalDate.of(2026, 10, 23);
        var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    // Ambas transacciones comprueban que el día falta antes de intentar insertarlo.
                    assertThat(priceRepository.countByInstrument(instrument)).isZero();
                    ready.countDown();
                    try {
                        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    priceRepository.upsert(instrument, nucoBar(date, "4200"), "YAHOO_FINANCE", OffsetDateTime.now());
                })));
            }
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
        assertThat(priceRepository.countByInstrument(instrument)).isEqualTo(1);
        assertThat(syncService.prices("COP=X", date, date).prices().getFirst().close()).isEqualByComparingTo("4200");
    }

    @Test
    @EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql:.*")
    void savesDollarConcurrentlyOnFirstAndSubsequentRefreshes() throws Exception {
        var date = LocalDate.of(2026, 10, 23);
        for (var close : List.of("4200", "4300")) {
            var series = new MarketDataSeries("COP=X", "USD/COP", "COP", "CCY", "CURRENCY", "Europe/London",
                    new BigDecimal(close), List.of(nucoBar(date, close)));
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(4)) {
                var tasks = new ArrayList<Future<Integer>>();
                for (int i = 0; i < 4; i++) {
                    tasks.add(executor.submit(() -> {
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        return persistenceService.save(series, "YAHOO_FINANCE");
                    }));
                }
                start.countDown();
                for (var task : tasks) assertThat(task.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            }
            assertThat(instrumentRepository.count()).isEqualTo(1);
            assertThat(priceRepository.count()).isEqualTo(1);
            assertThat(syncService.prices("COP=X", date, date).prices().getFirst().close()).isEqualByComparingTo(close);
        }
    }

    private void createPurchase() {
        createPurchase("ECOPETROL.CL");
    }

    private void createPurchase(String ticker) {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "mercado-test.xlsx",
                String.format("%064x", ticker.hashCode()),
                1,
                "admin"));
        operationRepository.save(new PortfolioOperation(
                portfolio,
                batch,
                LocalDate.of(2026, 8, 24),
                OperationType.COMPRA,
                ticker,
                "Ecopetrol",
                new BigDecimal("100"),
                new BigDecimal("2600"),
                BigDecimal.ZERO,
                new BigDecimal("260000"),
                null));
    }

    private DailyMarketBar nucoBar(LocalDate date, String close) {
        var price = new BigDecimal(close);
        return new DailyMarketBar(date, price, price, price, price, price, 100L, true);
    }

    private MarketDataSeries series() {
        return new MarketDataSeries(
                "ECOPETROL.CL",
                "Ecopetrol S.A.",
                "COP",
                "BVC",
                "EQUITY",
                "America/Bogota",
                new BigDecimal("2605"),
                List.of(
                        new DailyMarketBar(
                                LocalDate.of(2026, 8, 24),
                                new BigDecimal("2650"),
                                new BigDecimal("2690"),
                                new BigDecimal("2600"),
                                new BigDecimal("2690"),
                                new BigDecimal("2690"),
                                17_409_723L,
                                true),
                        new DailyMarketBar(
                                LocalDate.of(2026, 8, 25),
                                new BigDecimal("2690"),
                                new BigDecimal("2690"),
                                new BigDecimal("2605"),
                                new BigDecimal("2605"),
                                new BigDecimal("2605"),
                                18_610_466L,
                                true)));
    }
}
