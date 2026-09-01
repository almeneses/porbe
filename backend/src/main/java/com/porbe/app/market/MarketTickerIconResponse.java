package com.porbe.app.market;

import java.time.OffsetDateTime;

/** Confirma el estado del ícono editable de un ticker. */
public record MarketTickerIconResponse(
        String ticker,
        boolean hasIcon,
        OffsetDateTime updatedAt) {
}
