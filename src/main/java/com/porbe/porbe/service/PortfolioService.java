package com.porbe.porbe.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.porbe.porbe.model.Portfolio;
import com.porbe.porbe.model.PortfolioStock;
import com.porbe.porbe.model.Stock;
import com.porbe.porbe.model.StockPrice;
import com.porbe.porbe.repo.PortfolioRepository;
import com.porbe.porbe.repo.StockPriceRepository;

@Service
public class PortfolioService {
    @Autowired
    private StockPriceRepository stockPriceRepo;

    @Autowired
    private PortfolioRepository portfolioRepo;

    // public List<StockPrice> getPortfolioWeeklySummary(Long portfolioId) {
    //     List<StockPrice> stockPricesNow = portfolioRepo.findStockPriceByPortfolioAndDate(portfolioId, LocalDate.now());
    //     LocalDate monday = LocalDate.now().minusDays(2);
    //     List<Long> stockIds = stockPricesNow.stream().map(sp -> sp.getStock().getId()).toList();
    //     List<StockPrice> stockPricesMonday = stockPriceRepo.findByDateAndStock_idIn(monday, stockIds);
    //     Stock bestPerfStock = getBestPerfStock(stockPricesMonday, stockPricesNow);
        
    //     // Set pStocks = portfolio.getPortfolioStocks().stream().map(ps ->
    //     // ps.get).toArray();
    //     // List<Long> pStockIds = pStocks.stream().map(entry -> entry.)
    //     // List<StockPrice> pStockPrices =
    //     // stockPriceRepo.findByDateAndStock_idIn(LocalDate.now(), pStockIds);

    // }

    private Stock getBestPerfStock(List<StockPrice> initialSp, List<StockPrice> finalSp){
        return new Stock();
    }
}
