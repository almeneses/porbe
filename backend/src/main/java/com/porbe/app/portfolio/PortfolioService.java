package com.porbe.app.portfolio;

import org.springframework.stereotype.Service;

/** Resuelve y crea cuando hace falta el portafolio principal de la instalación. */
@Service
public class PortfolioService {

    static final String DEFAULT_PORTFOLIO_NAME = "Portafolio principal";

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public Portfolio getOrCreateDefaultPortfolio() {
        return portfolioRepository.findFirstByNameOrderByIdAsc(DEFAULT_PORTFOLIO_NAME)
                .orElseGet(() -> portfolioRepository.save(new Portfolio(DEFAULT_PORTFOLIO_NAME, "COP")));
    }
}
