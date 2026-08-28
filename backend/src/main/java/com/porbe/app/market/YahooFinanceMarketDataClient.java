package com.porbe.app.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
/** Adaptador HTTP que consume y normaliza la API de gráficos de Yahoo Finance. */
public class YahooFinanceMarketDataClient implements MarketDataProvider {

    private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z0-9^][A-Z0-9.^=\\-]{0,29}");
    private static final Duration FINAL_CLOSE_GRACE_PERIOD = Duration.ofMinutes(15);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public YahooFinanceMarketDataClient(
            @Qualifier("yahooFinanceRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String source() {
        return "YAHOO_FINANCE";
    }

    @Override
    /** Consulta el intervalo diario usando marcas UTC para evitar desfases entre bolsas. */
    public MarketDataSeries fetchDaily(String ticker, LocalDate from, LocalDate toExclusive) {
        var normalizedTicker = normalizeTicker(ticker);
        if (from == null || toExclusive == null || !from.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El rango de fechas para precios no es válido.");
        }

        try {
            var body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{ticker}")
                            .queryParam("period1", from.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                            .queryParam("period2", toExclusive.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                            .queryParam("interval", "1d")
                            .queryParam("events", "div,splits")
                            .queryParam("includeAdjustedClose", "true")
                            .build(normalizedTicker))
                    .retrieve()
                    .body(String.class);
            return parseResponse(normalizedTicker, body);
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MarketDataProviderException(
                    "Yahoo Finance no respondió para " + normalizedTicker + ". Intenta nuevamente.", exception);
        } catch (Exception exception) {
            throw new MarketDataProviderException(
                    "Yahoo Finance devolvió datos no válidos para " + normalizedTicker + ".", exception);
        }
    }

    /** Alinea por índice timestamps y arreglos OHLCV, omitiendo puntos sin cierre. */
    private MarketDataSeries parseResponse(String ticker, String body) {
        if (body == null || body.isBlank()) {
            throw new MarketDataProviderException("Yahoo Finance devolvió una respuesta vacía para " + ticker + ".");
        }

        var root = objectMapper.readTree(body);
        var chart = root.path("chart");
        var error = chart.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            var description = text(error, "description", "Símbolo no disponible");
            throw new MarketDataProviderException("Yahoo Finance: " + description + " (" + ticker + ").");
        }

        var results = chart.path("result");
        if (!results.isArray() || results.isEmpty()) {
            throw new MarketDataProviderException("Yahoo Finance no encontró datos para " + ticker + ".");
        }

        var result = results.get(0);
        var meta = result.path("meta");
        var exchangeTimezone = text(meta, "exchangeTimezoneName", "UTC");
        var exchangeZone = zone(exchangeTimezone);
        var regularEnd = longValue(meta.path("currentTradingPeriod").path("regular").path("end"));
        var timestamps = result.path("timestamp");
        var quote = first(result.path("indicators").path("quote"));
        var adjusted = first(result.path("indicators").path("adjclose")).path("adjclose");
        var bars = new ArrayList<DailyMarketBar>();

        if (timestamps.isArray() && quote.isObject()) {
            for (int index = 0; index < timestamps.size(); index++) {
                var timestamp = longAt(timestamps, index);
                var close = decimalAt(quote.path("close"), index);
                if (timestamp == null || close == null) {
                    continue;
                }
                var date = Instant.ofEpochSecond(timestamp).atZone(exchangeZone).toLocalDate();
                bars.add(new DailyMarketBar(
                        date,
                        decimalAt(quote.path("open"), index),
                        decimalAt(quote.path("high"), index),
                        decimalAt(quote.path("low"), index),
                        close,
                        decimalAt(adjusted, index),
                        longAt(quote.path("volume"), index),
                        isFinalClose(date, exchangeZone, regularEnd)));
            }
        }

        return new MarketDataSeries(
                text(meta, "symbol", ticker).toUpperCase(Locale.ROOT),
                text(meta, "longName", text(meta, "shortName", ticker)),
                nullableText(meta, "currency"),
                text(meta, "exchangeName", text(meta, "fullExchangeName", null)),
                nullableText(meta, "instrumentType"),
                exchangeTimezone,
                decimal(meta.path("regularMarketPrice")),
                bars);
    }

    /**
     * Considera provisional el precio del día hasta que termine la sesión más
     * un margen corto para que Yahoo publique el cierre definitivo.
     */
    private boolean isFinalClose(LocalDate priceDate, ZoneId exchangeZone, Long regularEnd) {
        var now = Instant.now(clock);
        var today = now.atZone(exchangeZone).toLocalDate();
        if (priceDate.isBefore(today)) {
            return true;
        }
        return priceDate.equals(today)
                && regularEnd != null
                && !now.isBefore(Instant.ofEpochSecond(regularEnd).plus(FINAL_CLOSE_GRACE_PERIOD));
    }

    private String normalizeTicker(String ticker) {
        var normalized = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El ticker de Yahoo Finance no es válido.");
        }
        return normalized;
    }

    private ZoneId zone(String value) {
        try {
            return ZoneId.of(value);
        } catch (ZoneRulesException exception) {
            return ZoneOffset.UTC;
        }
    }

    private JsonNode first(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0) : objectMapper.missingNode();
    }

    private BigDecimal decimalAt(JsonNode array, int index) {
        return array.isArray() && index < array.size() ? decimal(array.get(index)) : null;
    }

    private Long longAt(JsonNode array, int index) {
        return array.isArray() && index < array.size() ? longValue(array.get(index)) : null;
    }

    private BigDecimal decimal(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    private Long longValue(JsonNode node) {
        return node != null && node.isNumber() ? node.longValue() : null;
    }

    private String nullableText(JsonNode parent, String field) {
        var node = parent.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String text(JsonNode parent, String field, String fallback) {
        var value = nullableText(parent, field);
        return value == null ? fallback : value;
    }
}
