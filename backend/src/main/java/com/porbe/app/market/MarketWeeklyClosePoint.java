package com.porbe.app.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Último cierre disponible para un viernes calendario. */
public record MarketWeeklyClosePoint(
        LocalDate weekEnding,
        BigDecimal closePrice,
        LocalDate priceDate,
        boolean provisional) {
}
