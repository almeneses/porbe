package com.porbe.app.market;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TreeMap;
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
        instrumentRepository.ensureExists(series.ticker(), syncedAt);
        var instrument = instrumentRepository.findByTicker(series.ticker()).orElseThrow();
        instrument.updateFrom(series, syncedAt);

        if (series.bars().isEmpty()) {
            return 0;
        }

        var barsByDate = new TreeMap<LocalDate, DailyMarketBar>();
        // Una respuesta puede repetir el día actual: conserva su última cotización.
        series.bars().forEach(bar -> barsByDate.put(bar.date(), bar));
        barsByDate.values().forEach(bar -> priceRepository.upsert(instrument, bar, source, syncedAt));
        return barsByDate.size();
    }
}
