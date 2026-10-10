package com.porbe.app.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Extrae la serie pública de NUCO en COP incluida en el HTML de Stock Analysis. */
@Component
public class StockAnalysisMarketDataClient implements MarketDataProvider {

    private static final Pattern HISTORY = Pattern.compile(
            "symbol:\\s*\"BVC-NUCO\",\\s*source:\\s*\"[^\"]+\",[^\\[\\]]*?data:\\s*(\\[[^\\[\\]]*\\])");
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES,
                    JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
            .build();
    private final RestClient restClient;
    private final Clock clock;

    public StockAnalysisMarketDataClient(
            @Qualifier("stockAnalysisRestClient") RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    @Override
    public String source() {
        return "STOCK_ANALYSIS";
    }

    @Override
    public MarketDataSeries fetchDaily(String ticker, LocalDate from, LocalDate toExclusive) {
        if (ticker == null || !"NUCO.CL".equals(ticker.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Stock Analysis solo está configurado para NUCO.CL en COP.");
        }
        if (from == null || toExclusive == null || !from.isBefore(toExclusive)) {
            throw new IllegalArgumentException("El rango de fechas para precios no es válido.");
        }
        try {
            var html = restClient.get().uri("/quote/bvc/NUCO/history/").retrieve().body(String.class);
            return parseResponse(html, from, toExclusive);
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MarketDataProviderException(
                    "Stock Analysis no respondió para NUCO.CL. Los precios guardados se conservan.", exception);
        } catch (RuntimeException exception) {
            throw new MarketDataProviderException(
                    "Stock Analysis devolvió datos no válidos para NUCO.CL. Los precios guardados se conservan.", exception);
        }
    }

    private MarketDataSeries parseResponse(String html, LocalDate from, LocalDate toExclusive) {
        if (html == null || !html.contains("exchange_symbol:\"BVC:NUCO\"")
                || !html.contains("curr:{main:\"COP\",price:\"COP\"")) {
            throw new MarketDataProviderException("Stock Analysis no identificó NUCO en el mercado colombiano y en COP.");
        }
        var match = HISTORY.matcher(html);
        if (!match.find()) {
            throw new MarketDataProviderException("No se encontró el histórico de NUCO en Stock Analysis. Revisa el formato de la página.");
        }
        // shortcut: la página pública expone unos seis meses, usar una fuente licenciada para ampliar el histórico.
        var rows = mapper.readTree(match.group(1));
        var today = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota")));
        var prices = new TreeMap<LocalDate, DailyMarketBar>();
        for (var row : rows) {
            var date = LocalDate.parse(row.path("t").asText());
            var open = price(row, "o");
            var high = price(row, "h");
            var low = price(row, "l");
            var close = price(row, "c");
            var volume = volume(row);
            if (date.isAfter(today) || high.compareTo(low) < 0
                    || open.compareTo(low) < 0 || open.compareTo(high) > 0
                    || close.compareTo(low) < 0 || close.compareTo(high) > 0) {
                throw new MarketDataProviderException("Stock Analysis devolvió una vela no válida para NUCO.CL en " + date + ".");
            }
            var bar = new DailyMarketBar(date, open, high, low, close,
                    row.path("a").isNull() || row.path("a").isMissingNode() ? null : price(row, "a"),
                    volume, date.isBefore(today));
            if (prices.put(date, bar) != null) {
                throw new MarketDataProviderException("Stock Analysis devolvió fechas duplicadas para NUCO.CL.");
            }
        }
        if (prices.isEmpty()) {
            throw new MarketDataProviderException("Stock Analysis no devolvió un histórico válido para NUCO.CL.");
        }
        if (!toExclusive.isAfter(prices.firstKey())) {
            throw new MarketDataProviderException("Stock Analysis solo dispone de precios de NUCO.CL desde " + prices.firstKey() + ".");
        }
        return new MarketDataSeries("NUCO.CL", "Nu Holdings Ltd.", "COP", "BVC", "EQUITY",
                "America/Bogota", prices.lastEntry().getValue().close(),
                List.copyOf(prices.subMap(from, true, toExclusive, false).values()));
    }

    private BigDecimal price(JsonNode row, String field) {
        var value = row.path(field);
        if (!value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new MarketDataProviderException("Stock Analysis devolvió un precio no válido para NUCO.CL.");
        }
        return value.decimalValue();
    }

    private long volume(JsonNode row) {
        var value = row.path("v");
        if (!value.isNumber() || value.decimalValue().signum() < 0) {
            throw new MarketDataProviderException("Stock Analysis devolvió un volumen no válido para NUCO.CL.");
        }
        // La fuente introduce ruido decimal en cantidades enteras, por ejemplo 63298.99999999999.
        var rounded = value.decimalValue().setScale(0, RoundingMode.HALF_UP);
        if (value.decimalValue().subtract(rounded).abs().compareTo(new BigDecimal("0.000001")) > 0) {
            throw new MarketDataProviderException("Stock Analysis devolvió un volumen fraccionario para NUCO.CL.");
        }
        return rounded.longValueExact();
    }
}
