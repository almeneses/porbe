package com.porbe.app.report;

import java.util.List;

/** Modelo de presentación desacoplado de los cálculos financieros del informe. */
public record PortfolioReportTemplateModel(
        String portfolioName,
        String period,
        String statusLabel,
        String statusTone,
        String periodGain,
        String periodReturn,
        String periodTone,
        Asset bestImpact,
        Asset worstImpact,
        String accumulatedDividends,
        String accumulatedGain,
        String accumulatedReturn,
        String portfolioValue,
        String netContributions,
        String cashBalance,
        Chart historicalChart,
        List<Breakdown> assetAllocation,
        List<Breakdown> sectorAllocation,
        int movementCount,
        List<Movement> movements,
        Note note,
        String valuationMessage,
        String valuationTone) {

    /** Activo preparado para un bloque destacado y su avatar opcional. */
    public record Asset(
            boolean available,
            String ticker,
            String name,
            String result,
            String tone,
            String initials,
            String iconDataUri) {
    }

    /** Serie SVG ya escalada para que la plantilla no contenga lógica matemática. */
    public record Chart(
            boolean empty,
            String portfolioPoints,
            String contributionPoints,
            String areaPoints,
            String firstDate,
            String lastDate,
            List<GridLine> gridLines) {
    }

    /** Etiqueta y posición vertical de una guía del gráfico. */
    public record GridLine(
            double y,
            String label) {
    }

    /** Fila legible que explica cuánto representa una acción o sector. */
    public record Breakdown(
            String key,
            String name,
            String percentage,
            String width,
            String initials,
            String iconDataUri) {
    }

    /** Movimiento breve mostrado al final del resumen. */
    public record Movement(
            String type,
            String ticker,
            String title,
            String detail,
            String tone,
            String initials,
            String iconDataUri) {
    }

    /** Comentario opcional que traduce el resultado del periodo a palabras sencillas. */
    public record Note(
            String title,
            String body,
            String suggestion) {
    }
}
