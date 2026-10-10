package com.porbe.app.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StockAnalysisMarketDataClientTest {

    private MockRestServiceServer server;
    private StockAnalysisMarketDataClient client;
    private String html;

    @BeforeEach
    void setUp() throws IOException {
        var builder = RestClient.builder().baseUrl("https://stockanalysis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StockAnalysisMarketDataClient(builder.build(),
                Clock.fixed(Instant.parse("2026-10-09T21:00:00Z"), ZoneOffset.UTC));
        html = new ClassPathResource("market/stock-analysis-nuco.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void extractsEmbeddedPricesInDateOrderAndKeepsTodayProvisional() {
        expectHtml(html);
        var series = client.fetchDaily(" nuco.cl ", LocalDate.of(2024, 1, 19), LocalDate.of(2026, 10, 10));

        assertThat(client.source()).isEqualTo("STOCK_ANALYSIS");
        assertThat(series.ticker()).isEqualTo("NUCO.CL");
        assertThat(series.currency()).isEqualTo("COP");
        assertThat(series.exchange()).isEqualTo("BVC");
        assertThat(series.bars()).extracting(DailyMarketBar::date).containsExactly(
                LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9));
        var first = series.bars().getFirst();
        assertThat(first.open()).isEqualByComparingTo("50700");
        assertThat(first.high()).isEqualByComparingTo("51000");
        assertThat(first.low()).isEqualByComparingTo("49940");
        assertThat(first.close()).isEqualByComparingTo("50400");
        assertThat(first.adjustedClose()).isEqualByComparingTo("50400");
        assertThat(first.volume()).isEqualTo(127221L);
        assertThat(first.finalClose()).isTrue();
        assertThat(series.bars().getLast().finalClose()).isFalse();
        server.verify();
    }

    @Test
    void includesFromAndExcludesTo() {
        expectHtml(html);
        var series = client.fetchDaily("NUCO.CL", LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9));
        assertThat(series.bars()).hasSize(1);
        assertThat(series.bars().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(series.bars().getFirst().close()).isEqualByComparingTo("49500");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"currency", "symbol", "format", "price", "volume", "negative-volume", "large-volume", "duplicate", "future", "ohlc"})
    void rejectsUnexpectedPagesAndInvalidBars(String invalid) {
        var body = switch (invalid) {
            case "currency" -> html.replace("price:\"COP\"", "price:\"USD\"");
            case "symbol" -> html.replace("BVC-NUCO", "NYSE-NU");
            case "format" -> "<html>Access denied</html>";
            case "price" -> html.replace("c:50980", "c:null");
            case "volume" -> html.replace("v:62361", "v:1.5");
            case "negative-volume" -> html.replace("v:62361", "v:-1");
            case "large-volume" -> html.replace("v:62361", "v:9223372036854775808");
            case "duplicate" -> html.replace("2026-10-08", "2026-10-07");
            case "future" -> html.replace("2026-10-09", "2026-10-10");
            default -> html.replace("c:50980", "c:51820");
        };
        expectHtml(body);
        assertThatThrownBy(() -> client.fetchDaily("NUCO.CL", LocalDate.of(2024, 1, 19), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(MarketDataProviderException.class).hasMessageContaining("Stock Analysis");
        server.verify();
    }

    @Test
    void normalizesOnlyRoundingNoiseInShareVolumes() {
        expectHtml(html.replace("v:62361", "v:63298.99999999999")
                .replace("v:54514", "v:64849.00000000001"));
        var series = client.fetchDaily("NUCO.CL", LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 10));
        assertThat(series.bars().getFirst().volume()).isEqualTo(64849L);
        assertThat(series.bars().getLast().volume()).isEqualTo(63299L);
        assertThat(series.bars().getLast().close()).isEqualByComparingTo("50980");
        server.verify();
    }

    @Test
    void reportsTheAvailableWindowWhenTheRequestedHistoryIsOlder() {
        expectHtml(html);
        assertThatThrownBy(() -> client.fetchDaily("NUCO.CL", LocalDate.of(2024, 1, 19), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(MarketDataProviderException.class).hasMessageContaining("desde 2026-10-07");
        server.verify();
    }

    @Test
    void reportsHttpFailureWithoutReturningAnEmptySeries() {
        server.expect(requestTo("https://stockanalysis.com/quote/bvc/NUCO/history/"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.fetchDaily("NUCO.CL", LocalDate.of(2024, 1, 19), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(MarketDataProviderException.class).hasMessageContaining("se conservan");
        server.verify();
    }

    @Test
    void rejectsTheUsTickerAndInvalidRangesBeforeMakingAnyRequest() {
        assertThatThrownBy(() -> client.fetchDaily("NU", LocalDate.of(2024, 1, 19), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchDaily("NUCO.CL", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private void expectHtml(String body) {
        server.expect(requestTo("https://stockanalysis.com/quote/bvc/NUCO/history/"))
                .andRespond(withSuccess(body, MediaType.TEXT_HTML));
    }
}
