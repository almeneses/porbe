package com.porbe.app.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Estado de actualización y último cierre disponible para un ticker. */
public record MarketTickerStatus(
        String ticker,
        String name,
        String currency,
        String exchange,
        LocalDate firstOperationDate,
        LocalDate lastPriceDate,
        BigDecimal lastClose,
        boolean provisional,
        long storedDays,
        OffsetDateTime lastSyncedAt) {
}
