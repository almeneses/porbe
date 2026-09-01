package com.porbe.app.portfolio;

import java.time.OffsetDateTime;

/** Metadatos livianos usados por el selector de portafolios. */
public record PortfolioResponse(
        Long id,
        String name,
        String baseCurrency,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }
}
