package com.porbe.app.report;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Metadatos livianos de un informe disponibles para la pantalla de historial. */
public record PortfolioReportListItem(
        Long id,
        Long portfolioId,
        String portfolioName,
        LocalDate from,
        LocalDate to,
        LocalDate valuationDate,
        String baseCurrency,
        String triggerType,
        String status,
        String generatedBy,
        boolean valuationComplete,
        int provisionalPrices,
        int imageSize,
        int pdfSize,
        String deliveryStatus,
        String deliveryMessage,
        String errorMessage,
        OffsetDateTime generatedAt,
        OffsetDateTime createdAt) {
}
