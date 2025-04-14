package com.porbe.porbe.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.porbe.porbe.model.StockPrice;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    List<StockPrice> findByStockIdAndDateBetween(Long stockId, LocalDate start, LocalDate end);
    List<StockPrice> findByDateAndStock_idIn(LocalDate date, List<Long> stockIds);
}
