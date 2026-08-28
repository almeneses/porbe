package com.porbe.app.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vela diaria normalizada recibida desde un proveedor externo. */
public record DailyMarketBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        Long volume,
        boolean finalClose) {
}
