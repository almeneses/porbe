package com.porbe.app.portfolio;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Expone la valoración actual y las posiciones del portafolio principal. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioSummaryController {

    private final PortfolioValuationService valuationService;
    private final PortfolioHistoryService historyService;

    public PortfolioSummaryController(
            PortfolioValuationService valuationService,
            PortfolioHistoryService historyService) {
        this.valuationService = valuationService;
        this.historyService = historyService;
    }

    @GetMapping("/summary")
    PortfolioSummaryResponse summary(@RequestParam(required = false) Long portfolioId) {
        return valuationService.currentSummary(portfolioId);
    }

    @GetMapping("/history/weekly")
    PortfolioHistoryResponse weeklyHistory(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return historyService.weeklyHistory(portfolioId, from, to);
    }
}
