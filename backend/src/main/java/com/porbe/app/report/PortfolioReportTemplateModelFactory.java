package com.porbe.app.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formatea los datos calculados y prepara las coordenadas consumidas por Thymeleaf. */
@Component
public class PortfolioReportTemplateModelFactory {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter PERIOD_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH);
    private static final DateTimeFormatter CHART_DATE = DateTimeFormatter.ofPattern("dd MMM yy", SPANISH);
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final double CHART_WIDTH = 900d;
    private static final double CHART_LEFT = 100d;
    private static final double CHART_TOP = 10d;
    private static final double CHART_BOTTOM = 210d;

    public PortfolioReportTemplateModel create(PortfolioReportData data) {
        return create(data, null);
    }

    /** Permite incluir una nota preparada sin hacerla obligatoria en los informes normales. */
    public PortfolioReportTemplateModel create(
            PortfolioReportData data,
            PortfolioReportTemplateModel.Note note) {
        var periodTone = tone(data.periodGain());
        return new PortfolioReportTemplateModel(
                data.portfolioName().toUpperCase(SPANISH),
                capitalize(data.from().format(PERIOD_DATE)) + " - " + capitalize(data.to().format(PERIOD_DATE)),
                data.valuationComplete() ? "DATOS AL DÍA" : "REVISAR DATOS",
                data.valuationComplete() ? "positive" : "negative",
                signedMoney(data.periodGain(), data.baseCurrency()),
                signedPercent(data.periodReturn()),
                periodTone,
                impactAsset(data.bestPeriodImpact(), true),
                impactAsset(data.worstPeriodImpact(), false),
                compactMoney(data.accumulatedDividends()),
                compactMoney(data.accumulatedGain()),
                percent(data.accumulatedReturn()),
                compactMoney(data.portfolioValue()),
                compactMoney(data.netContributions()),
                compactMoney(data.cashBalance()),
                chart(data.chart()),
                data.assetAllocation().stream().map(this::breakdown).toList(),
                data.sectorAllocation().stream().map(this::breakdown).toList(),
                data.movementCount(),
                data.movements().stream().map(this::movement).toList(),
                note,
                valuationMessage(data),
                data.valuationComplete() ? "neutral" : "negative");
    }

    private PortfolioReportTemplateModel.Asset impactAsset(
            PortfolioReportAssetHighlight asset,
            boolean positive) {
        if (asset == null) {
            return new PortfolioReportTemplateModel.Asset(
                    false,
                    "-",
                    positive ? "Todavía no hay una acción destacada" : "Ninguna acción redujo el resultado",
                    positive ? "Sin aumento registrado" : "Buena señal para este periodo",
                    positive ? "neutral" : "positive",
                    positive ? "-" : "✓",
                    null);
        }
        return new PortfolioReportTemplateModel.Asset(
                true,
                asset.ticker(),
                asset.name() == null || asset.name().isBlank() ? asset.ticker() : asset.name(),
                (positive ? "Sumó " : "Restó ") + compactMoney(asset.amount().abs()),
                positive ? "positive" : "negative",
                initials(asset.ticker()),
                iconDataUri(asset.icon()));
    }

    private PortfolioReportTemplateModel.Breakdown breakdown(PortfolioReportAllocation allocation) {
        var percentage = allocation.rate().multiply(BigDecimal.valueOf(100));
        return new PortfolioReportTemplateModel.Breakdown(
                allocation.key(),
                allocation.name(),
                new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(SPANISH)).format(percentage) + "%",
                String.format(Locale.ROOT, "%.2f%%", percentage.doubleValue()),
                allocation.icon() == null ? wordInitials(allocation.name()) : initials(allocation.key()),
                iconDataUri(allocation.icon()));
    }

    private PortfolioReportTemplateModel.Movement movement(PortfolioReportMovement movement) {
        var ticker = movement.ticker();
        var title = capitalize(movement.type()) + (ticker == null ? "" : " · " + ticker);
        var detail = movement.quantity() == null
                ? compactMoney(movement.totalAmount())
                : formatQuantity(movement.quantity()) + " acciones · " + compactMoney(movement.totalAmount());
        return new PortfolioReportTemplateModel.Movement(
                movement.type(),
                ticker,
                title,
                detail,
                movementTone(movement.type()),
                ticker == null ? null : initials(ticker),
                iconDataUri(movement.icon()));
    }

    /** Escala ambas series sobre el mismo máximo y devuelve polilíneas SVG deterministas. */
    private PortfolioReportTemplateModel.Chart chart(List<PortfolioReportChartPoint> points) {
        if (points.isEmpty()) {
            return new PortfolioReportTemplateModel.Chart(true, "", "", "", "", "", List.of());
        }
        var maximum = points.stream()
                .flatMap(point -> java.util.stream.Stream.of(point.portfolioValue(), point.netContributions()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE)
                .max(BigDecimal.ONE);
        var portfolioPoints = new ArrayList<String>();
        var contributionPoints = new ArrayList<String>();
        for (var index = 0; index < points.size(); index++) {
            var point = points.get(index);
            var x = points.size() == 1
                    ? (CHART_LEFT + CHART_WIDTH) / 2
                    : CHART_LEFT + (CHART_WIDTH - CHART_LEFT) * index / (points.size() - 1d);
            portfolioPoints.add(svgPoint(x, coordinate(point.portfolioValue(), maximum)));
            contributionPoints.add(svgPoint(x, coordinate(point.netContributions(), maximum)));
        }
        var portfolioLine = String.join(" ", portfolioPoints);
        var area = svgPoint(CHART_LEFT, CHART_BOTTOM) + " " + portfolioLine + " "
                + svgPoint(CHART_WIDTH, CHART_BOTTOM);
        var gridLines = new ArrayList<PortfolioReportTemplateModel.GridLine>();
        for (var index = 0; index <= 4; index++) {
            var y = CHART_TOP + (CHART_BOTTOM - CHART_TOP) * index / 4d;
            var value = maximum.multiply(BigDecimal.valueOf(4 - index))
                    .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            gridLines.add(new PortfolioReportTemplateModel.GridLine(y, compactMoney(value)));
        }
        return new PortfolioReportTemplateModel.Chart(
                false,
                portfolioLine,
                String.join(" ", contributionPoints),
                area,
                points.getFirst().date().format(CHART_DATE),
                points.getLast().date().format(CHART_DATE),
                List.copyOf(gridLines));
    }

    private double coordinate(BigDecimal value, BigDecimal maximum) {
        var normalized = value.max(BigDecimal.ZERO)
                .divide(maximum, 8, RoundingMode.HALF_UP)
                .doubleValue();
        return CHART_BOTTOM - normalized * (CHART_BOTTOM - CHART_TOP);
    }

    private String svgPoint(double x, double y) {
        return String.format(Locale.ROOT, "%.2f,%.2f", x, y);
    }

    private String valuationMessage(PortfolioReportData data) {
        if (data.valuationComplete()) {
            return "Precios actualizados hasta el " + data.valuationDate().format(PERIOD_DATE);
        }
        if (data.unpricedPositions() == 0 && data.provisionalPrices() == 0) {
            return "Hay movimientos que necesitan revisión antes de completar el informe";
        }
        return "Faltan precios para " + data.unpricedPositions() + " acciones y "
                + data.provisionalPrices() + " precios todavía son aproximados";
    }

    private String signedMoney(BigDecimal value, String currency) {
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "-" : "")
                + money(value.abs(), currency);
    }

    private String money(BigDecimal value, String currency) {
        var formatted = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(SPANISH)).format(value);
        return "COP".equals(currency) ? "$ " + formatted : currency + " " + formatted;
    }

    private String compactMoney(BigDecimal value) {
        var safeValue = value == null ? BigDecimal.ZERO : value;
        var absolute = safeValue.abs();
        var divisor = absolute.compareTo(ONE_MILLION) >= 0
                ? ONE_MILLION
                : absolute.compareTo(ONE_THOUSAND) >= 0 ? ONE_THOUSAND : BigDecimal.ONE;
        var suffix = divisor.equals(ONE_MILLION) ? " M" : divisor.compareTo(BigDecimal.ONE) > 0 ? " mil" : "";
        var amount = safeValue.divide(divisor, 1, RoundingMode.HALF_UP);
        return "$ " + new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(SPANISH)).format(amount)
                + suffix;
    }

    private String percent(BigDecimal value) {
        var safeValue = value == null ? BigDecimal.ZERO : value;
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(SPANISH))
                .format(safeValue.multiply(BigDecimal.valueOf(100))) + "%";
    }

    private String signedPercent(BigDecimal value) {
        var safeValue = value == null ? BigDecimal.ZERO : value;
        return (safeValue.signum() > 0 ? "+" : "") + percent(safeValue);
    }

    private String formatQuantity(BigDecimal value) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(SPANISH)).format(value);
    }

    private String movementTone(String type) {
        return switch (type.toLowerCase(SPANISH)) {
            case "compra", "depósito" -> "positive";
            case "venta", "retiro" -> "negative";
            default -> "accent";
        };
    }

    private String tone(BigDecimal value) {
        return value != null && value.signum() < 0 ? "negative" : "positive";
    }

    private String initials(String ticker) {
        var clean = ticker.replace(".CL", "").replaceAll("[^A-Za-z0-9]", "");
        if (clean.isBlank()) {
            return "-";
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase(SPANISH);
    }

    private String wordInitials(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return java.util.Arrays.stream(value.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .limit(2)
                .map(word -> word.substring(0, 1))
                .collect(java.util.stream.Collectors.joining())
                .toUpperCase(SPANISH);
    }

    private String iconDataUri(byte[] icon) {
        if (icon == null || icon.length == 0) {
            return null;
        }
        var mime = icon.length >= 2 && (icon[0] & 0xff) == 0xff && (icon[1] & 0xff) == 0xd8
                ? "image/jpeg"
                : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(icon);
    }

    private String capitalize(String value) {
        return value == null || value.isBlank()
                ? value
                : value.substring(0, 1).toUpperCase(SPANISH) + value.substring(1);
    }
}
