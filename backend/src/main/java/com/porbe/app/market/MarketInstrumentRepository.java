package com.porbe.app.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso persistente a instrumentos identificados por su ticker. */
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrument, Long> {

    Optional<MarketInstrument> findByTicker(String ticker);

    List<MarketInstrument> findByTickerIn(Collection<String> tickers);
}
