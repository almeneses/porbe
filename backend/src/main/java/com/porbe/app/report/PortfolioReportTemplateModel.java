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
        String totalPerformance,
        String netContributions,
        Chart historicalChart,
        List<Performance> gainsByAsset,
        List<Performance> dividendsByAsset,
        List<Breakdown> assetAllocation,
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
            EndLabel portfolioEnd,
            EndLabel contributionEnd,
            List<GridLine> gridLines) {
    }

    /** Valor rotulado al final de una serie del gráfico. */
    public record EndLabel(
            double pointX,
            double pointY,
            double labelX,
            double labelY,
            String value) {
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
            String color,
            String donutDash,
            String donutOffset,
            double donutLabelX,
            double donutLabelY) {
    }

    /** Barra proporcional para comparar montos acumulados entre acciones. */
    public record Performance(
            String name,
            String amount,
            String width,
            String tone,
            String color) {
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
