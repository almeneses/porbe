package com.porbe.app.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de cobertura y precios diarios ordenados por fecha. */
public interface MarketPriceDailyRepository extends JpaRepository<MarketPriceDaily, Long> {

    List<MarketPriceDaily> findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(
            MarketInstrument instrument,
            LocalDate from,
            LocalDate to);

    Optional<MarketPriceDaily> findTopByInstrumentOrderByPriceDateDesc(MarketInstrument instrument);

    long countByInstrument(MarketInstrument instrument);
}
