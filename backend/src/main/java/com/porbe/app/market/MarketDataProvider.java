package com.porbe.app.market;

import java.time.LocalDate;

/** Puerto que desacopla la aplicación de la fuente concreta de cotizaciones. */
public interface MarketDataProvider {

    String source();

    MarketDataSeries fetchDaily(String ticker, LocalDate from, LocalDate toExclusive);
}
