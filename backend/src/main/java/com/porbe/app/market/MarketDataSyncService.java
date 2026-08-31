package com.porbe.app.market;

import com.porbe.app.operation.PortfolioOperationRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordina la cobertura requerida, consulta al proveedor y reporta resultados. */
@Service
public class MarketDataSyncService {

    public static final LocalDate MARKET_HISTORY_START = LocalDate.of(2024, 1, 19);

    private final PortfolioOperationRepository operationRepository;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceDailyRepository priceRepository;
    private final MarketDataProvider provider;
    private final MarketDataPersistenceService persistenceService;
    private final Clock clock;

    public MarketDataSyncService(
            PortfolioOperationRepository operationRepository,
            MarketInstrumentRepository instrumentRepository,
            MarketPriceDailyRepository priceRepository,
            MarketDataProvider provider,
            MarketDataPersistenceService persistenceService,
            Clock clock) {
        this.operationRepository = operationRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.provider = provider;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    /**
     * Actualiza cada ticker de forma independiente para reportar fallos parciales
     * sin perder los precios válidos obtenidos para los demás activos.
     */
    public MarketDataSyncResponse syncPortfolio() {
        var tickerRanges = operationRepository.findPortfolioTickerRanges();
        if (tickerRanges.isEmpty()) {
            return new MarketDataSyncResponse(
                    0,
                    0,
                    0,
                    "No hay tickers en el portafolio. Importa operaciones antes de actualizar precios.",
                    java.util.List.of());
        }

        var results = new ArrayList<TickerSyncResult>();
        var totalStored = 0;
        var successful = 0;
        var toExclusive = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Bogota")).plusDays(1);

        for (var tickerRange : tickerRanges) {
            var ticker = tickerRange.getTicker().toUpperCase(Locale.ROOT);
            var from = MARKET_HISTORY_START;
            try {
                var series = provider.fetchDaily(ticker, from, toExclusive);
                var stored = persistenceService.save(series, provider.source());
                totalStored += stored;
                successful++;
                results.add(new TickerSyncResult(
                        ticker,
                        true,
                        stored,
                        stored == 0 ? "Yahoo Finance no devolvió cierres para el rango." : "Precios actualizados."));
            } catch (RuntimeException exception) {
                results.add(new TickerSyncResult(ticker, false, 0, safeMessage(exception)));
            }
        }

        var message = successful == tickerRanges.size()
                ? "Datos de mercado actualizados correctamente."
                : "La actualización terminó con novedades en uno o más tickers.";
        return new MarketDataSyncResponse(tickerRanges.size(), successful, totalStored, message, results);
    }

    @Transactional(readOnly = true)
    /** Combina operaciones, instrumentos y último precio en una vista compacta. */
    public MarketDataStatusResponse status() {
        var tickerRanges = operationRepository.findPortfolioTickerRanges();
        var instrumentsByTicker = new HashMap<String, MarketInstrument>();
        instrumentRepository.findByTickerIn(tickerRanges.stream().map(range -> range.getTicker().toUpperCase(Locale.ROOT)).toList())
                .forEach(instrument -> instrumentsByTicker.put(instrument.getTicker(), instrument));

        var statuses = tickerRanges.stream()
                .map(range -> {
                    var ticker = range.getTicker().toUpperCase(Locale.ROOT);
                    var instrument = instrumentsByTicker.get(ticker);
                    if (instrument == null) {
                        return new MarketTickerStatus(
                                ticker,
                                null,
                                null,
                                null,
                                MarketSectorCatalog.suggestedSector(ticker),
                                range.getFirstOperationDate(),
                                null,
                                null,
                                false,
                                0,
                                null);
                    }
                    var latest = priceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument).orElse(null);
                    return new MarketTickerStatus(
                            ticker,
                            instrument.getName(),
                            instrument.getCurrency(),
                            instrument.getExchange(),
                            instrument.getSector(),
                            range.getFirstOperationDate(),
                            latest == null ? null : latest.getPriceDate(),
                            latest == null ? null : latest.getClose(),
                            latest != null && !latest.isFinalClose(),
                            priceRepository.countByInstrument(instrument),
                            instrument.getLastSyncedAt());
                })
                .toList();

        return new MarketDataStatusResponse(
                provider.source(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                statuses);
    }

    @Transactional(readOnly = true)
    public TickerPricesResponse prices(String ticker, LocalDate from, LocalDate to) {
        var normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        var instrument = instrumentRepository.findByTicker(normalizedTicker)
                .orElseThrow(() -> new MarketDataNotFoundException(
                        "No hay datos guardados para " + normalizedTicker + "."));
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        var prices = priceRepository
                .findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(instrument, from, to)
                .stream()
                .map(DailyPriceResponse::from)
                .toList();
        return new TickerPricesResponse(
                instrument.getTicker(),
                instrument.getName(),
                instrument.getCurrency(),
                instrument.getExchange(),
                prices);
    }

    /**
     * Proyecta el último cierre conocido sobre cada viernes para todos los
     * símbolos actuales, incluso antes de su primera operación en el libro.
     */
    @Transactional(readOnly = true)
    public MarketWeeklyClosesResponse weeklyCloses() {
        var ranges = operationRepository.findPortfolioTickerRanges();
        var tickers = ranges.stream()
                .map(range -> range.getTicker().toUpperCase(Locale.ROOT))
                .toList();
        var instruments = new HashMap<String, MarketInstrument>();
        instrumentRepository.findByTickerIn(tickers)
                .forEach(instrument -> instruments.put(instrument.getTicker(), instrument));
        var lastFriday = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Bogota"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        var fridays = fridays(MARKET_HISTORY_START, lastFriday);
        var complete = true;
        var series = new ArrayList<MarketTickerWeeklyCloses>();

        for (var ticker : tickers) {
            var instrument = instruments.get(ticker);
            var prices = priceIndex(instrument, lastFriday);
            var closes = new ArrayList<MarketWeeklyClosePoint>();
            for (var friday : fridays) {
                var entry = prices.floorEntry(friday);
                var price = entry == null ? null : entry.getValue();
                if (price == null) {
                    complete = false;
                }
                closes.add(new MarketWeeklyClosePoint(
                        friday,
                        price == null ? null : price.getClose(),
                        price == null ? null : price.getPriceDate(),
                        price != null && !price.isFinalClose()));
            }
            series.add(new MarketTickerWeeklyCloses(
                    ticker,
                    instrument == null ? null : instrument.getName(),
                    instrument == null ? null : instrument.getCurrency(),
                    instrument == null
                            ? MarketSectorCatalog.suggestedSector(ticker)
                            : instrument.getSector(),
                    closes));
        }

        return new MarketWeeklyClosesResponse(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                MARKET_HISTORY_START,
                lastFriday,
                series.size(),
                fridays.size(),
                complete,
                series);
    }

    @Transactional
    public MarketSectorResponse updateSector(String ticker, String sector) {
        var normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        var instrument = instrumentRepository.findByTicker(normalizedTicker)
                .orElseGet(() -> new MarketInstrument(normalizedTicker));
        instrument.updateSector(sector);
        var saved = instrumentRepository.save(instrument);
        return new MarketSectorResponse(saved.getTicker(), saved.getSector());
    }

    private NavigableMap<LocalDate, MarketPriceDaily> priceIndex(
            MarketInstrument instrument,
            LocalDate lastFriday) {
        if (instrument == null) {
            return new TreeMap<>();
        }
        var prices = new TreeMap<LocalDate, MarketPriceDaily>();
        priceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateAsc(instrument, lastFriday)
                .forEach(price -> prices.put(price.getPriceDate(), price));
        return prices;
    }

    private List<LocalDate> fridays(LocalDate from, LocalDate to) {
        var result = new ArrayList<LocalDate>();
        for (var date = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
                !date.isAfter(to);
                date = date.plusWeeks(1)) {
            result.add(date);
        }
        return result;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "No fue posible consultar este ticker."
                : exception.getMessage();
    }
}
