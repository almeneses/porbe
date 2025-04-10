package com.porbe.porbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.porbe.porbe.service.StockPriceService;

@SpringBootApplication
public class PorbeApplication {

	public static void main(String[] args) {
		ApplicationContext appCpntext = SpringApplication.run(PorbeApplication.class, args);
		StockPriceService stockPriceService = appCpntext.getBean(StockPriceService.class);
		System.out.println(stockPriceService.scrapeStockPrice("CELSIA.CL"));
	}

}
