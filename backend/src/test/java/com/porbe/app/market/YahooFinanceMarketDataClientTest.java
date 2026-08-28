package com.porbe.app.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Verifica la normalización de respuestas válidas y erróneas de Yahoo Finance. */
class YahooFinanceMarketDataClientTest {

    private MockRestServiceServer server;
    private YahooFinanceMarketDataClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YahooFinanceMarketDataClient(
                builder.build(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void parsesBvcDailyPricesAndMetadata() {
        server.expect(requestTo(containsString("/v8/finance/chart/ECOPETROL.CL")))
                .andExpect(queryParam("interval", "1d"))
                .andExpect(queryParam("events", "div,splits"))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        var series = client.fetchDaily(
                "ecopetrol.cl",
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 28));

        assertThat(series.ticker()).isEqualTo("ECOPETROL.CL");
        assertThat(series.currency()).isEqualTo("COP");
        assertThat(series.exchange()).isEqualTo("BVC");
        assertThat(series.name()).isEqualTo("Ecopetrol S.A.");
        assertThat(series.bars()).hasSize(2);
        assertThat(series.bars().getFirst().close()).isEqualByComparingTo("2690");
        assertThat(series.bars().getLast().volume()).isEqualTo(18_610_466L);
        assertThat(series.bars()).allMatch(DailyMarketBar::finalClose);
        server.verify();
    }

    @Test
    void reportsYahooChartErrorsInSpanish() {
        server.expect(requestTo(containsString("/v8/finance/chart/NOEXISTE.CL")))
                .andRespond(withSuccess("""
                        {"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchDaily(
                "NOEXISTE.CL",
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 28)))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("No data found")
                .hasMessageContaining("NOEXISTE.CL");
        server.verify();
    }

    private String validResponse() {
        return """
                {
                  "chart": {
                    "result": [{
                      "meta": {
                        "currency": "COP",
                        "symbol": "ECOPETROL.CL",
                        "exchangeName": "BVC",
                        "instrumentType": "EQUITY",
                        "exchangeTimezoneName": "America/New_York",
                        "regularMarketPrice": 2605.0,
                        "longName": "Ecopetrol S.A.",
                        "currentTradingPeriod": {"regular": {"end": 1787860800}}
                      },
                      "timestamp": [1787578200, 1787664600],
                      "indicators": {
                        "quote": [{
                          "open": [2650.0, 2690.0],
                          "high": [2690.0, 2650.0],
                          "low": [2600.0, 2605.0],
                          "close": [2690.0, 2605.0],
                          "volume": [17409723, 18610466]
                        }],
                        "adjclose": [{"adjclose": [2690.0, 2605.0]}]
                      }
                    }],
                    "error": null
                  }
                }
                """;
    }
}
