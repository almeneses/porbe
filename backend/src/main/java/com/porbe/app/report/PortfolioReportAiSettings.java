package com.porbe.app.report;

/** Selección persistida utilizada al redactar el comentario del informe. */
public record PortfolioReportAiSettings(boolean enabled, String model, String effort) {
}
