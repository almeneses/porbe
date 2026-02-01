package com.porbe.porbe.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.porbe.porbe.model.Stock;
import com.porbe.porbe.model.StockPrice;
import com.porbe.porbe.repo.StockPriceRepository;
import com.porbe.porbe.repo.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

    private static final String BASE_URL = "https://finance.yahoo.com/quote/%s";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";
    private static final String PRICE_CSS_QUERY = "span[data-testid=qsp-price]";

    private final StockRepository stockRepo;
    private final StockPriceRepository stockPriceRepo;

    public Optional<StockPrice> scrapeStockPrice(String ticker) {
        log.info("Getting stock price for: {}", ticker);

        LocalDate today = LocalDate.now();
        String tickerUrl = String.format(BASE_URL, ticker);

        try {
            Document document = Jsoup.connect(tickerUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .timeout(30000)
                    .get();

            String priceStr = document.selectFirst(PRICE_CSS_QUERY)
                    .text()
                    .replace(",", "");

            if (priceStr != null && !priceStr.isEmpty()) {
                double closePrice = Double.parseDouble(priceStr);
                Stock stock = stockRepo.findByTicker(ticker).orElse(
                        Stock.builder()
                                .name(ticker)
                                .ticker(ticker)
                                .currency("COP")
                                .build());
                StockPrice stockPrice = StockPrice.builder()
                        .stock(stock)
                        .date(today)
                        .closePrice(closePrice)
                        .build();

                log.info("Successfully got {} price: {}", ticker, closePrice);

                return Optional.of(stockPrice);
            }
        } catch (IOException e) {
            log.error("Error getting stock price for {}: {}", ticker, e.getMessage());
        } catch (NumberFormatException e) {
            log.error("Error parsing stock price for {}: {}", ticker, e.getMessage());
        }

        return Optional.empty();
    }

    @Async
    @Scheduled(cron = "${stock.scheduler.cron.daily}", zone = "America/Bogota")
    @Retryable(
        retryFor = { IOException.class, RuntimeException.class }, 
        maxAttempts = 4, 
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void fetchDailyStockPrices() {
        log.info("Starting daily stock price fetch.");
        
        List<Stock> stocks = stockRepo.findAll();

        for (Stock stock : stocks) {
            try {
                scrapeStockPrice(stock.getTicker()).ifPresent(stockPriceRepo::save);

                // Delay to prevent rate limiting
                // TODO: Make this random between 3-10 seconds
                Thread.sleep(3000);

            } catch (Exception e) {
                log.error("Error processing ticker {}: {}", stock.getTicker(), e.getMessage());
            }
        }

        log.info("Completed daily stock price fetch");

    }

    @Async
    @Scheduled(cron = "0 * * * * *", zone = "America/Bogota")
    public void test(){
        System.out.println("Running!");
    }

    public List<StockPrice> getStockPricesBetween(String ticker, LocalDate startDate, LocalDate endDate) {
        return stockPriceRepo.findByStock_TickerAndDateBetween(ticker, startDate, endDate);
    }

    public List<StockPrice> getLastStockPrices(){
        LocalDate lastFriday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
        LocalDateTime startOfDay = lastFriday.atStartOfDay();
        LocalDateTime endOfDay = lastFriday.atTime(23, 59, 59);

        return stockPriceRepo.findAllByCreatedAtBetween(startOfDay, endOfDay);
    }

    @Recover
    public void recover(Exception e){
        System.err.println("Scraping totally failed after retries: " + e.getMessage());
    }

    public StockPrice bestPerfStockPrice(List<StockPrice> initial, List<StockPrice> current){
        StockPrice currentBest = null;
        Double priceChange = 0d;
        for (int i = 0; i < current.size(); i++) {
            if(currentBest == null){
                currentBest = current.get(i);
            } else {
                Double change = (current.get(i).getClosePrice() - initial.get(i).getClosePrice()) / initial.get(i).getClosePrice();
                if(change > priceChange){
                    currentBest = current.get(i);
                }
            }
        }

        return currentBest;
    }
}
