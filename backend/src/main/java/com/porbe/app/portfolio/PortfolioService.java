package com.porbe.app.portfolio;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administra los portafolios disponibles y conserva uno principal compatible con datos previos. */
@Service
public class PortfolioService {

    static final String DEFAULT_PORTFOLIO_NAME = "Portafolio principal";

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public Portfolio getOrCreateDefaultPortfolio() {
        return portfolioRepository.findFirstByNameOrderByIdAsc(DEFAULT_PORTFOLIO_NAME)
                .or(() -> portfolioRepository.findAllByOrderByCreatedAtAscIdAsc().stream().findFirst())
                .orElseGet(() -> portfolioRepository.save(new Portfolio(DEFAULT_PORTFOLIO_NAME, "COP")));
    }

    /** Resuelve el portafolio solicitado; un identificador ausente mantiene compatibilidad con clientes anteriores. */
    public Portfolio getPortfolio(Long portfolioId) {
        if (portfolioId == null) {
            return getOrCreateDefaultPortfolio();
        }
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("El portafolio seleccionado no existe."));
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> list() {
        var portfolios = portfolioRepository.findAllByOrderByCreatedAtAscIdAsc();
        if (portfolios.isEmpty()) {
            return List.of(PortfolioResponse.from(getOrCreateDefaultPortfolio()));
        }
        return portfolios.stream().map(PortfolioResponse::from).toList();
    }

    @Transactional
    public PortfolioResponse create(String requestedName) {
        var name = validatedName(requestedName);
        if (portfolioRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Ya existe un portafolio con ese nombre.");
        }
        return PortfolioResponse.from(portfolioRepository.save(new Portfolio(name, "COP")));
    }

    @Transactional
    public PortfolioResponse rename(Long portfolioId, String requestedName) {
        var portfolio = getPortfolio(portfolioId);
        var name = validatedName(requestedName);
        if (!portfolio.getName().equalsIgnoreCase(name) && portfolioRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Ya existe un portafolio con ese nombre.");
        }
        portfolio.rename(name);
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    private String validatedName(String requestedName) {
        var name = requestedName == null ? "" : requestedName.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Escribe un nombre para el portafolio.");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("El nombre del portafolio no puede superar 120 caracteres.");
        }
        return name;
    }
}
