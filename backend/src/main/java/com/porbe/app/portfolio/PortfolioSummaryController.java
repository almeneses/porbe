package com.porbe.app.portfolio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
/** Expone la valoración actual y las posiciones del portafolio principal. */
public class PortfolioSummaryController {

    private final PortfolioValuationService valuationService;

    public PortfolioSummaryController(PortfolioValuationService valuationService) {
        this.valuationService = valuationService;
    }

    @GetMapping("/summary")
    PortfolioSummaryResponse summary() {
        return valuationService.currentSummary();
    }
}
