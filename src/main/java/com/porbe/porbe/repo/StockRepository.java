package com.porbe.porbe.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.porbe.porbe.model.Stock;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByTicker(String ticker);
}
