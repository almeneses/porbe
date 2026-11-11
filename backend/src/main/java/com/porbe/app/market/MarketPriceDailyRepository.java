package com.porbe.app.market;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Consultas de cobertura y precios diarios ordenados por fecha. */
public interface MarketPriceDailyRepository extends JpaRepository<MarketPriceDaily, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            insert into MarketPriceDaily
                (instrument, priceDate, open, high, low, close, adjustedClose, volume, finalClose, source, fetchedAt)
            values (:instrument, :#{#bar.date()}, :#{#bar.open()}, :#{#bar.high()}, :#{#bar.low()},
                :#{#bar.close()}, :#{#bar.adjustedClose()}, :#{#bar.volume()}, :#{#bar.finalClose()}, :source, :fetchedAt)
            on conflict (instrument, priceDate) do update set
                open = excluded.open, high = excluded.high, low = excluded.low, close = excluded.close,
                adjustedClose = excluded.adjustedClose, volume = excluded.volume,
                finalClose = excluded.finalClose, source = excluded.source, fetchedAt = excluded.fetchedAt
            """)
    void upsert(MarketInstrument instrument, DailyMarketBar bar, String source, OffsetDateTime fetchedAt);

    List<MarketPriceDaily> findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(
            MarketInstrument instrument,
            LocalDate from,
            LocalDate to);

    List<MarketPriceDaily> findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateAsc(
            MarketInstrument instrument,
            LocalDate to);

    Optional<MarketPriceDaily> findTopByInstrumentOrderByPriceDateDesc(MarketInstrument instrument);

    Optional<MarketPriceDaily> findTopByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
            MarketInstrument instrument, LocalDate cutoff);

    long countByInstrument(MarketInstrument instrument);
}
