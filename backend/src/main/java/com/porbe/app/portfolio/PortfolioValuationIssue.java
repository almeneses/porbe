package com.porbe.app.portfolio;

/** Advertencia que impide calcular con precisión una posición del portafolio. */
public record PortfolioValuationIssue(String ticker, String code, String message) {
}
