package com.porbe.app.market;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Acceso persistente a instrumentos identificados por su ticker. */
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrument, Long> {

    /** Crea o bloquea el ticker para coordinar también su primera sincronización concurrente. */
    @Modifying
    @Query("""
            insert into MarketInstrument (ticker, createdAt, updatedAt)
            values (:ticker, :syncedAt, :syncedAt)
            on conflict (ticker) do update set ticker = excluded.ticker
            """)
    void ensureExists(String ticker, OffsetDateTime syncedAt);

    Optional<MarketInstrument> findByTicker(String ticker);

    List<MarketInstrument> findByTickerIn(Collection<String> tickers);
}
