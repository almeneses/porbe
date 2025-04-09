package com.porbe.porbe.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.porbe.porbe.model.StockPrice;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

}
