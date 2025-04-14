package com.porbe.porbe.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.porbe.porbe.model.Portfolio;
import com.porbe.porbe.model.StockPrice;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    @Query("""
        SELECT sp FROM StockPrice sp WHERE sp.stock.id IN (
            SELECT ps.stock.id FROM PortfolioStock ps
            WHERE ps.portfolio.id = :portfolioId 
            AND sp.date = :closeDate
        )""")
    List<StockPrice> findStockPriceByPortfolioAndDate(Long portfolioId, LocalDate closeDate); 
}



//select sp from portfolio p inner join portfolio_stock pst on p.id = pst.portfolio_id inner join stockprice sp on pst.stock_id = sp.stock_id where p.id = xx;