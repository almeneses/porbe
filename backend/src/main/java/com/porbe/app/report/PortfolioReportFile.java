package com.porbe.app.report;

/** Contenido descargable junto con su tipo y nombre sugerido. */
public record PortfolioReportFile(byte[] content, String contentType, String filename) {
}
