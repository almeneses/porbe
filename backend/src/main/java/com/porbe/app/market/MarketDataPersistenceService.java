package com.porbe.app.market;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Conserva metadatos y precios diarios sin duplicar fechas ya registradas. */
public class MarketDataPersistenceService {

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceDailyRepository priceRepository;
    private final Clock clock;

    public MarketDataPersistenceService(
            MarketInstrumentRepository instrumentRepository,
            MarketPriceDailyRepository priceRepository,
            Clock clock) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @Transactional
    /** Actualiza por fecha los precios existentes y crea únicamente los faltantes. */
    public int save(MarketDataSeries series, String source) {
        var syncedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var instrument = instrumentRepository.findByTicker(series.ticker())
                .orElseGet(() -> new MarketInstrument(series.ticker()));
        instrument.updateFrom(series, syncedAt);
        var persistedInstrument = instrumentRepository.save(instrument);

        if (series.bars().isEmpty()) {
            return 0;
        }

        var barsByDate = new TreeMap<LocalDate, DailyMarketBar>();
        // Una respuesta puede repetir el día actual: conserva su última cotización.
        series.bars().forEach(bar -> barsByDate.put(bar.date(), bar));
        var from = barsByDate.firstKey();
        var to = barsByDate.lastKey();
        var existingByDate = priceRepository
                .findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(persistedInstrument, from, to)
                .stream()
                .collect(Collectors.toMap(MarketPriceDaily::getPriceDate, Function.identity()));

        var prices = barsByDate.values().stream()
                .map(bar -> {
                    var existing = existingByDate.get(bar.date());
                    if (existing == null) {
                        return new MarketPriceDaily(persistedInstrument, bar, source, syncedAt);
                    }
                    existing.update(bar, source, syncedAt);
                    return existing;
                })
                .toList();
        priceRepository.saveAll(prices);
        return prices.size();
    }
}
