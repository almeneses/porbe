package com.porbe.app.market;

import java.time.LocalDate;

/** Contrato común para obtener precios sin cambiar la sincronización ni su persistencia. */
public interface MarketDataProvider {

    String source();

    MarketDataSeries fetchDaily(String ticker, LocalDate from, LocalDate toExclusive);
}
