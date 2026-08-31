package com.porbe.app.market;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    private MarketDataProvider provider;

    @BeforeEach
    void cleanDatabase() {
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        portfolioRepository.deleteAll();
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
        org.assertj.core.api.Assertions.assertThat(priceRepository.count()).isEqualTo(2);
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

    private void createPurchase() {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "mercado-test.xlsx",
                "b2f0012411f6591eeac13c17ddf0f4907f22718512442e9afe15a6c027d7a551",
                1,
                "admin"));
        operationRepository.save(new PortfolioOperation(
                portfolio,
                batch,
                LocalDate.of(2026, 8, 24),
                OperationType.COMPRA,
                "ECOPETROL.CL",
                "Ecopetrol",
                new BigDecimal("100"),
                new BigDecimal("2600"),
                BigDecimal.ZERO,
                new BigDecimal("260000"),
                null));
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
