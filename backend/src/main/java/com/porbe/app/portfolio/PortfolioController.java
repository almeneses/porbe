package com.porbe.app.portfolio;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Expone la creación, consulta y cambio de nombre de portafolios. */
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    List<PortfolioResponse> list() {
        return portfolioService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PortfolioResponse create(@Valid @RequestBody PortfolioRequest request) {
        return portfolioService.create(request.name());
    }

    @PutMapping("/{id}")
    PortfolioResponse rename(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.rename(id, request.name());
    }
}
