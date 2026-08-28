package com.porbe.app.market;

import com.porbe.app.operation.PortfolioOperationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Coordina la cobertura requerida, consulta al proveedor y reporta resultados. */
public class MarketDataSyncService {

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
            var from = tickerRange.getFirstOperationDate().minusDays(7);
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
                                ticker, null, null, null, range.getFirstOperationDate(), null, null, false, 0, null);
                    }
                    var latest = priceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument).orElse(null);
                    return new MarketTickerStatus(
                            ticker,
                            instrument.getName(),
                            instrument.getCurrency(),
                            instrument.getExchange(),
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

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "No fue posible consultar este ticker."
                : exception.getMessage();
    }
}
