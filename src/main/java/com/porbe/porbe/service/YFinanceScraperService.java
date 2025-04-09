package com.porbe.porbe.service;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Evaluator;
import org.springframework.stereotype.Service;

import com.porbe.porbe.model.StockPrice;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class YFinanceScraperService {
    private static final String BASE_URL = "https://finance.yahoo.com/quote/%s";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";
    private static final String PRICE_REGEX = "/<fin-streamer[^>]*data-testid=\"qsp-price\"[^>]*value=\"([\\d.,]+)\"[^>]*>/i";

    public Optional<StockPrice> scrapeStockPrice(String ticker) {
        log.info("Getting stock price for: {}", ticker);

        String tickerUrl = String.format(BASE_URL, ticker);

        try {
            Document document = Jsoup.connect(tickerUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .timeout(30000)
                    .get();
            Evaluator evaluator = new Evaluator.Matches(Pattern.compile(PRICE_REGEX));
            String priceStr = document.select(evaluator).get(1).text();

            if (priceStr != null && !priceStr.isEmpty()) {
                double closePrice = Double.parseDouble(priceStr);
                StockPrice stockPrice = StockPrice.builder()
                        .ticker(ticker)
                        .closePrice(closePrice)
                        .build();

            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
