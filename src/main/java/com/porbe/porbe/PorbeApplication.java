package com.porbe.porbe;

import java.time.LocalDate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.porbe.porbe.repo.StockPriceRepository;
import com.porbe.porbe.service.PortfolioService;
import com.porbe.porbe.service.StockPriceService;

@SpringBootApplication
public class PorbeApplication {

	public static void main(String[] args) {
		ApplicationContext appContext = SpringApplication.run(PorbeApplication.class, args);
		StockPriceService stockPriceService = appContext.getBean(StockPriceService.class);
		stockPriceService.fetchDailyStockPrices();
		PortfolioService portfolioService = appContext.getBean(PortfolioService.class);
		StockPriceRepository stockPriceRepo = appContext.getBean(StockPriceRepository.class);
		//System.out.println(portfolioService.getPortfolioWeeklySummary(1L));
		System.out.println("------------------------------");
		System.out.println(stockPriceService.scrapeStockPrice("CELSIA.CL"));
	}

}
