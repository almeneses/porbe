package com.porbe.porbe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.porbe.porbe.service.StockPriceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class BasicController {

    private final StockPriceService stockPriceService;

    @GetMapping("/stock/fetch")
    public ResponseEntity<String> triggerStockPriceFetch() {
        stockPriceService.fetchDailyStockPrices();

        return ResponseEntity.ok("Stock price fetch triggered successfully");
    }
}
