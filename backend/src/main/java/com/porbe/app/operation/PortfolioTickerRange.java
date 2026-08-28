package com.porbe.app.operation;

import java.time.LocalDate;

/** Proyección de la primera operación y el nombre conocido de cada ticker. */
public interface PortfolioTickerRange {

    String getTicker();

    LocalDate getFirstOperationDate();
}
