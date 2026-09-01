package com.porbe.app.report;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;

/** Dibuja el informe vertical con la paleta y jerarquía visual de Porbe. */
@Component
public class PortfolioReportImageRenderer {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 2280;

    private static final Color BACKGROUND = color("#f5f1e9");
    private static final Color SURFACE = color("#fffdf9");
    private static final Color TEXT = color("#172a36");
    private static final Color TEXT_SOFT = color("#68746f");
    private static final Color BORDER = color("#ded7cc");
    private static final Color GOLD = color("#c49a3a");
    private static final Color NAVY = color("#0d3755");
    private static final Color NAVY_DEEP = color("#0a304a");
    private static final Color GREEN = color("#2d896c");
    private static final Color CORAL = color("#c84b43");
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter PERIOD_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH);

    public BufferedImage render(PortfolioReportData data) {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            header(graphics, data);
            periodResult(graphics, data);
            highlights(graphics, data);
            accumulated(graphics, data);
            chart(graphics, data);
            closingMetrics(graphics, data);
            movements(graphics, data);
            footer(graphics, data);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private void header(Graphics2D graphics, PortfolioReportData data) {
        graphics.setColor(NAVY);
        graphics.fillRect(0, 0, WIDTH, 220);
        graphics.setColor(GOLD);
        graphics.fillRect(0, 0, 18, 220);
        graphics.fillRoundRect(60, 48, 62, 62, 16, 16);
        graphics.setColor(NAVY_DEEP);
        graphics.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(77, 90, 89, 76);
        graphics.drawLine(89, 76, 101, 87);
        graphics.drawLine(101, 87, 109, 67);
        text(graphics, trim(data.portfolioName().toUpperCase(SPANISH), 28), 144, 72, 24, Font.BOLD, SURFACE);
        text(graphics, "INFORME DE RENDIMIENTO", 144, 104, 14, Font.BOLD, color("#adc0cb"));
        text(graphics, "Resumen del portafolio", 60, 162, 46, Font.BOLD, SURFACE);
        var period = capitalize(data.from().format(PERIOD_DATE)) + " - "
                + capitalize(data.to().format(PERIOD_DATE));
        rightText(graphics, period, WIDTH - 60, 174, 22, Font.PLAIN, color("#d8c997"));
    }

    private void periodResult(Graphics2D graphics, PortfolioReportData data) {
        card(graphics, 60, 250, 960, 185);
        eyebrow(graphics, "RESULTADO DEL PERIODO", 92, 294);
        text(graphics, "¿Cómo se comportó tu portafolio?", 92, 340, 30, Font.BOLD, TEXT);
        var tone = data.periodGain().signum() >= 0 ? GREEN : CORAL;
        text(graphics, signedMoney(data.periodGain(), data.baseCurrency()), 92, 397, 40, Font.BOLD, tone);
        rightText(graphics, signedPercent(data.periodReturn()), 986, 397, 40, Font.BOLD, tone);
        rightText(graphics, "Rentabilidad TWR", 986, 420, 14, Font.BOLD, TEXT_SOFT);
    }

    private void highlights(Graphics2D graphics, PortfolioReportData data) {
        highlightCard(
                graphics,
                60,
                465,
                "VALORIZACIÓN EN EL PERIODO",
                data.bestAppreciation(),
                data.worstAppreciation());
        highlightCard(
                graphics,
                550,
                465,
                "RENTABILIDAD ACUMULADA",
                data.bestProfitability(),
                data.worstProfitability());
    }

    private void highlightCard(
            Graphics2D graphics,
            int x,
            int y,
            String title,
            PortfolioReportAssetHighlight best,
            PortfolioReportAssetHighlight worst) {
        card(graphics, x, y, 470, 270);
        eyebrow(graphics, title, x + 28, y + 42);
        assetHighlight(graphics, x + 28, y + 75, "Mayor", best, GREEN);
        assetHighlight(graphics, x + 250, y + 75, "Menor", worst, CORAL);
    }

    private void assetHighlight(
            Graphics2D graphics,
            int x,
            int y,
            String label,
            PortfolioReportAssetHighlight asset,
            Color tone) {
        text(graphics, label, x, y + 12, 13, Font.BOLD, TEXT_SOFT);
        drawTickerIcon(graphics, asset, x, y + 28, 58, tone);
        text(graphics, asset == null ? "Sin datos" : trim(asset.name() == null ? asset.ticker() : asset.name(), 21), x, y + 118, 17, Font.BOLD, TEXT);
        text(graphics, asset == null ? "-" : trim(asset.ticker(), 16), x, y + 142, 12, Font.PLAIN, TEXT_SOFT);
        text(graphics, asset == null ? "-" : signedPercent(asset.rate()), x, y + 174, 22, Font.BOLD, asset == null ? TEXT_SOFT : tone);
    }

    private void accumulated(Graphics2D graphics, PortfolioReportData data) {
        card(graphics, 60, 765, 960, 190);
        eyebrow(graphics, "ACUMULADO HASTA " + data.valuationDate().format(PERIOD_DATE).toUpperCase(SPANISH), 92, 808);
        metric(graphics, 92, 845, "Dividendos", compactMoney(data.accumulatedDividends()), GREEN);
        metric(graphics, 390, 845, "Ganancia total", compactMoney(data.accumulatedGain()), GREEN);
        metric(graphics, 700, 845, "Rentabilidad", percent(data.accumulatedReturn()), GREEN);
    }

    private void chart(Graphics2D graphics, PortfolioReportData data) {
        chartCard(graphics, 985, "Evolución histórica completa", "Desde la primera operación", data.chart());
        chartCard(graphics, 1375, "Evolución de los últimos 6 meses", "Detalle reciente del portafolio", data.sixMonthChart());
    }

    private void chartCard(
            Graphics2D graphics,
            int y,
            String title,
            String subtitle,
            List<PortfolioReportChartPoint> points) {
        var x = 60;
        var width = 960;
        var height = 360;
        card(graphics, x, y, width, height);
        text(graphics, title, x + 32, y + 47, 27, Font.BOLD, TEXT);
        text(graphics, subtitle, x + 32, y + 72, 13, Font.PLAIN, TEXT_SOFT);
        legend(graphics, x + 610, y + 43, GOLD, "Valor del portafolio");
        legend(graphics, x + 800, y + 43, TEXT_SOFT, "Capital aportado");

        var left = x + 48;
        var right = x + width - 36;
        var top = y + 100;
        var bottom = y + height - 52;
        for (var index = 0; index <= 4; index++) {
            var gridY = top + (bottom - top) * index / 4;
            graphics.setColor(BORDER);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawLine(left, gridY, right, gridY);
        }
        drawSeries(graphics, points, left, right, top, bottom);
        if (!points.isEmpty()) {
            text(graphics, points.getFirst().date().format(DateTimeFormatter.ofPattern("dd MMM yy", SPANISH)), left, bottom + 31, 13, Font.PLAIN, TEXT_SOFT);
            rightText(graphics, points.getLast().date().format(DateTimeFormatter.ofPattern("dd MMM yy", SPANISH)), right, bottom + 31, 13, Font.PLAIN, TEXT_SOFT);
        }
    }

    private void drawSeries(
            Graphics2D graphics,
            List<PortfolioReportChartPoint> points,
            int left,
            int right,
            int top,
            int bottom) {
        if (points.isEmpty()) {
            centeredText(graphics, "No hay puntos suficientes para el gráfico", (left + right) / 2, (top + bottom) / 2, 18, Font.PLAIN, TEXT_SOFT);
            return;
        }
        var maximum = points.stream()
                .flatMap(point -> java.util.stream.Stream.of(point.portfolioValue(), point.netContributions()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE)
                .max(BigDecimal.ONE);
        var valuePath = new Path2D.Double();
        var contributionPath = new Path2D.Double();
        var area = new Path2D.Double();
        for (var index = 0; index < points.size(); index++) {
            var point = points.get(index);
            var px = points.size() == 1 ? (left + right) / 2.0 : left + (right - left) * index / (double) (points.size() - 1);
            var valueY = coordinate(point.portfolioValue(), maximum, top, bottom);
            var contributionY = coordinate(point.netContributions(), maximum, top, bottom);
            if (index == 0) {
                valuePath.moveTo(px, valueY);
                contributionPath.moveTo(px, contributionY);
                area.moveTo(px, bottom);
                area.lineTo(px, valueY);
            } else {
                valuePath.lineTo(px, valueY);
                contributionPath.lineTo(px, contributionY);
                area.lineTo(px, valueY);
            }
            if (index == points.size() - 1) {
                area.lineTo(px, bottom);
                area.closePath();
            }
        }
        var previousComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
        graphics.setColor(GOLD);
        graphics.fill(area);
        graphics.setComposite(previousComposite);
        graphics.setColor(TEXT_SOFT);
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(contributionPath);
        graphics.setColor(GOLD);
        graphics.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(valuePath);
    }

    private void closingMetrics(Graphics2D graphics, PortfolioReportData data) {
        smallMetricCard(graphics, 60, 1765, "Aportes netos", compactMoney(data.netContributions()));
        smallMetricCard(graphics, 385, 1765, "Efectivo", compactMoney(data.cashBalance()));
        smallMetricCard(graphics, 710, 1765, "Valor del portafolio", compactMoney(data.portfolioValue()));
    }

    private void movements(Graphics2D graphics, PortfolioReportData data) {
        card(graphics, 60, 1960, 960, 230);
        text(graphics, "Movimientos del periodo", 92, 2008, 28, Font.BOLD, TEXT);
        rightText(graphics, data.movementCount() + " registrados", 988, 2007, 14, Font.BOLD, TEXT_SOFT);
        if (data.movements().isEmpty()) {
            text(graphics, "No hubo compras, ventas, dividendos, depósitos ni retiros.", 92, 2080, 17, Font.PLAIN, TEXT_SOFT);
            return;
        }
        for (var index = 0; index < data.movements().size(); index++) {
            var movement = data.movements().get(index);
            var column = index % 2;
            var row = index / 2;
            var itemX = 92 + column * 448;
            var itemY = 2040 + row * 66;
            graphics.setColor(movementTone(movement.type()));
            graphics.fillRoundRect(itemX, itemY, 8, 39, 8, 8);
            var contentX = itemX + 18;
            if (movement.ticker() != null) {
                drawTickerIcon(graphics, movement.ticker(), movement.icon(), contentX, itemY, 38, movementTone(movement.type()));
                contentX += 48;
            }
            var title = capitalize(movement.type()) + (movement.ticker() == null ? "" : " · " + movement.ticker());
            text(graphics, trim(title, 25), contentX, itemY + 16, 15, Font.BOLD, TEXT);
            var detail = movement.quantity() == null
                    ? compactMoney(movement.totalAmount())
                    : formatQuantity(movement.quantity()) + " acciones · " + compactMoney(movement.totalAmount());
            text(graphics, trim(detail, 30), contentX, itemY + 37, 13, Font.PLAIN, TEXT_SOFT);
        }
    }

    private void footer(Graphics2D graphics, PortfolioReportData data) {
        var message = data.valuationComplete()
                ? "Cierres completos al " + data.valuationDate().format(PERIOD_DATE)
                : partialValuationMessage(data);
        text(graphics, message, 60, 2242, 14, Font.PLAIN, data.valuationComplete() ? TEXT_SOFT : CORAL);
        rightText(graphics, "Fuente de precios: Yahoo Finance", WIDTH - 60, 2242, 14, Font.PLAIN, TEXT_SOFT);
    }

    /** Explica una valoración parcial incluso cuando el problema proviene de las operaciones y no de los precios. */
    private String partialValuationMessage(PortfolioReportData data) {
        if (data.unpricedPositions() == 0 && data.provisionalPrices() == 0) {
            return "Informe parcial: existen operaciones o datos que requieren revisión";
        }
        return "Informe parcial: " + data.unpricedPositions() + " posiciones sin precio y "
                + data.provisionalPrices() + " precios provisionales";
    }

    private void drawTickerIcon(
            Graphics2D graphics,
            PortfolioReportAssetHighlight asset,
            int x,
            int y,
            int size,
            Color tone) {
        drawTickerIcon(
                graphics,
                asset == null ? null : asset.ticker(),
                asset == null ? null : asset.icon(),
                x,
                y,
                size,
                asset == null ? BORDER : tone);
    }

    /** Recorta el ícono en círculo y usa iniciales cuando el ticker aún no tiene imagen. */
    private void drawTickerIcon(
            Graphics2D graphics,
            String ticker,
            byte[] icon,
            int x,
            int y,
            int size,
            Color tone) {
        var image = readIcon(icon);
        graphics.setColor(tone);
        graphics.fillOval(x, y, size, size);
        if (image != null) {
            Shape previousClip = graphics.getClip();
            graphics.clip(new Ellipse2D.Double(x, y, size, size));
            graphics.drawImage(image, x, y, size, size, null);
            graphics.setClip(previousClip);
            graphics.setColor(BORDER);
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.drawOval(x, y, size, size);
            return;
        }
        centeredText(
                graphics,
                ticker == null ? "-" : initials(ticker),
                x + size / 2,
                y + (int) (size * 0.65),
                Math.max(12, size / 3),
                Font.BOLD,
                SURFACE);
    }

    private BufferedImage readIcon(byte[] icon) {
        if (icon == null || icon.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(icon));
        } catch (IOException exception) {
            return null;
        }
    }

    private void metric(Graphics2D graphics, int x, int y, String label, String value, Color tone) {
        text(graphics, value, x, y + 33, 33, Font.BOLD, tone);
        text(graphics, label, x, y + 68, 16, Font.BOLD, TEXT_SOFT);
    }

    private void smallMetricCard(Graphics2D graphics, int x, int y, String label, String value) {
        card(graphics, x, y, 310, 160);
        text(graphics, label, x + 24, y + 46, 16, Font.BOLD, TEXT_SOFT);
        text(graphics, value, x + 24, y + 100, 29, Font.BOLD, NAVY);
    }

    private void card(Graphics2D graphics, int x, int y, int width, int height) {
        graphics.setColor(new Color(23, 42, 54, 12));
        graphics.fillRoundRect(x + 3, y + 8, width, height, 26, 26);
        graphics.setColor(SURFACE);
        graphics.fillRoundRect(x, y, width, height, 26, 26);
        graphics.setColor(BORDER);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawRoundRect(x, y, width, height, 26, 26);
    }

    private void legend(Graphics2D graphics, int x, int y, Color tone, String label) {
        graphics.setColor(tone);
        graphics.fillRoundRect(x, y, 24, 6, 6, 6);
        text(graphics, label, x + 34, y + 7, 11, Font.BOLD, TEXT_SOFT);
    }

    private void eyebrow(Graphics2D graphics, String value, int x, int y) {
        text(graphics, value, x, y, 13, Font.BOLD, GOLD);
    }

    private void text(Graphics2D graphics, String value, int x, int baseline, int size, int style, Color color) {
        graphics.setFont(new Font(style == Font.PLAIN ? Font.SANS_SERIF : Font.SERIF, style, size));
        graphics.setColor(color);
        graphics.drawString(value, x, baseline);
    }

    private void rightText(Graphics2D graphics, String value, int right, int baseline, int size, int style, Color color) {
        graphics.setFont(new Font(style == Font.PLAIN ? Font.SANS_SERIF : Font.SERIF, style, size));
        var width = graphics.getFontMetrics().stringWidth(value);
        text(graphics, value, right - width, baseline, size, style, color);
    }

    private void centeredText(Graphics2D graphics, String value, int centerX, int baseline, int size, int style, Color color) {
        graphics.setFont(new Font(style == Font.PLAIN ? Font.SANS_SERIF : Font.SERIF, style, size));
        var width = graphics.getFontMetrics().stringWidth(value);
        text(graphics, value, centerX - width / 2, baseline, size, style, color);
    }

    private double coordinate(BigDecimal value, BigDecimal maximum, int top, int bottom) {
        var normalized = value.max(BigDecimal.ZERO).divide(maximum, 8, RoundingMode.HALF_UP).doubleValue();
        return bottom - normalized * (bottom - top);
    }

    private String signedMoney(BigDecimal value, String currency) {
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "-" : "")
                + money(value.abs(), currency);
    }

    private String money(BigDecimal value, String currency) {
        var symbols = DecimalFormatSymbols.getInstance(SPANISH);
        var formatted = new DecimalFormat("#,##0", symbols).format(value);
        return currency.equals("COP") ? "$ " + formatted : currency + " " + formatted;
    }

    private String compactMoney(BigDecimal value) {
        var absolute = value.abs();
        var divisor = absolute.compareTo(new BigDecimal("1000000")) >= 0
                ? new BigDecimal("1000000")
                : absolute.compareTo(new BigDecimal("1000")) >= 0
                        ? new BigDecimal("1000")
                        : BigDecimal.ONE;
        var suffix = divisor.compareTo(new BigDecimal("1000000")) == 0 ? " M" : divisor.compareTo(BigDecimal.ONE) > 0 ? " mil" : "";
        var amount = value.divide(divisor, 1, RoundingMode.HALF_UP);
        return "$ " + new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(SPANISH)).format(amount) + suffix;
    }

    private String percent(BigDecimal value) {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(SPANISH))
                .format(value.multiply(BigDecimal.valueOf(100))) + "%";
    }

    private String signedPercent(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + percent(value);
    }

    private String formatQuantity(BigDecimal value) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(SPANISH)).format(value);
    }

    private Color movementTone(String type) {
        return switch (type.toLowerCase(SPANISH)) {
            case "compra", "depósito" -> GREEN;
            case "venta", "retiro" -> CORAL;
            default -> GOLD;
        };
    }

    private String initials(String ticker) {
        var clean = ticker.replace(".CL", "").replaceAll("[^A-Za-z0-9]", "");
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase(SPANISH);
    }

    private String trim(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }

    private String capitalize(String value) {
        return value == null || value.isBlank()
                ? value
                : value.substring(0, 1).toUpperCase(SPANISH) + value.substring(1);
    }

    private void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static Color color(String value) {
        return Color.decode(value);
    }
}
