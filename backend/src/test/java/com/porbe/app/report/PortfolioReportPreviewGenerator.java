package com.porbe.app.report;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Genera una maqueta ficticia para revisar visualmente el informe sin tocar la base de datos. */
@SpringBootTest
class PortfolioReportPreviewGenerator {

    private static final Path OUTPUT = Path.of("/tmp/porbe-informe-ficticio-completo.html");
    private static final Path OUTPUT_IMAGE = Path.of("/tmp/porbe-informe-ficticio-completo.png");

    @Autowired private PortfolioReportHtmlRenderer renderer;
    @Autowired private PortfolioReportArtifactRenderer artifactRenderer;

    @Test
    void generatesCompleteFictitiousReport() throws Exception {
        var note = new PortfolioReportTemplateModel.Note(
                "Un mes tranquilo y con buen crecimiento",
                "Tu portafolio terminó el periodo por encima de donde comenzó. Ecopetrol fue la acción que más ayudó y los dividendos también aportaron al resultado. Aunque Bancolombia bajó un poco, el balance general siguió siendo positivo.",
                List.of(
                        "No necesitas hacer cambios apresurados para el próximo mes.",
                        "Mantener el dinero repartido ayuda a reducir el efecto de una sola caída."));

        var data = sampleData();
        Files.writeString(OUTPUT, renderer.render(data, note));
        Files.write(OUTPUT_IMAGE, artifactRenderer.render(data, note).image());
    }

    /** Usa montos cercanos a un portafolio personal para que la composición se vea realista. */
    private PortfolioReportData sampleData() {
        return new PortfolioReportData(
                999L,
                "Portafolio familiar",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2024, 1, 19),
                LocalDate.of(2026, 8, 28),
                "COP",
                new BigDecimal("1482600"),
                new BigDecimal("0.0432"),
                highlight("ECOPETROL.CL", "Ecopetrol", "0.062", "890000"),
                highlight("PFBCOLOM.CL", "Bancolombia preferencial", "-0.018", "-260000"),
                new BigDecimal("1420000"),
                new BigDecimal("6480000"),
                new BigDecimal("0.2354"),
                new BigDecimal("0.2176"),
                new BigDecimal("31600000"),
                new BigDecimal("850000"),
                new BigDecimal("38750000"),
                4,
                movements(),
                chart(),
                List.of(
                        assetValue("ECOPETROL.CL", "Ecopetrol", "3120000"),
                        assetValue("GRUPOSURA.CL", "Grupo Sura", "1840000"),
                        assetValue("ISA.CL", "ISA", "1260000"),
                        assetValue("PFBCOLOM.CL", "Bancolombia preferencial", "-420000")),
                List.of(
                        assetValue("ECOPETROL.CL", "Ecopetrol", "680000"),
                        assetValue("ISA.CL", "ISA", "390000"),
                        assetValue("PFBCOLOM.CL", "Bancolombia preferencial", "230000"),
                        assetValue("GRUPOSURA.CL", "Grupo Sura", "120000")),
                List.of(
                        allocation("ECOPETROL.CL", "Ecopetrol", "0.34"),
                        allocation("PFBCOLOM.CL", "Bancolombia preferencial", "0.27"),
                        allocation("GRUPOSURA.CL", "Grupo Sura", "0.22"),
                        allocation("ISA.CL", "ISA", "0.17")),
                List.of(
                        allocation("Energía", "Energía", "0.34"),
                        allocation("Finanzas", "Servicios financieros", "0.27"),
                        allocation("Inversiones", "Grupo de inversiones", "0.22"),
                        allocation("Infraestructura", "Infraestructura", "0.17")),
                true,
                0,
                0);
    }

    private PortfolioReportAssetValue assetValue(String ticker, String name, String amount) {
        return new PortfolioReportAssetValue(ticker, name, new BigDecimal(amount));
    }

    private List<PortfolioReportMovement> movements() {
        return List.of(
                movement("compra", "ECOPETROL.CL", "Ecopetrol", "120", "3120000"),
                movement("venta", "PFBCOLOM.CL", "Bancolombia preferencial", "18", "716400"),
                movement("dividendo", "ISA.CL", "ISA", null, "186500"),
                movement("depósito", null, null, null, "1500000"));
    }

    /** Construye una historia ascendente con pequeñas variaciones para evitar una gráfica artificial. */
    private List<PortfolioReportChartPoint> chart() {
        var points = new ArrayList<PortfolioReportChartPoint>();
        var values = List.of(
                "8200000", "9100000", "10400000", "9800000", "11700000", "12500000",
                "12100000", "13900000", "15100000", "14700000", "16900000", "18100000",
                "17700000", "19800000", "21500000", "20700000", "23800000", "25100000",
                "24700000", "27600000", "29300000", "28600000", "31800000", "33200000",
                "32700000", "35100000", "36900000", "36200000", "38200000", "38750000");
        var contributions = List.of(
                "8000000", "8000000", "10000000", "10000000", "11500000", "11500000",
                "11500000", "13000000", "13000000", "13000000", "15000000", "15000000",
                "15000000", "17500000", "17500000", "17500000", "20500000", "20500000",
                "20500000", "23000000", "23000000", "23000000", "26000000", "26000000",
                "26000000", "28500000", "28500000", "28500000", "30100000", "31600000");
        var firstDate = LocalDate.of(2024, 1, 19);
        var lastDate = LocalDate.of(2026, 8, 28);
        var totalDays = ChronoUnit.DAYS.between(firstDate, lastDate);
        for (var index = 0; index < values.size(); index++) {
            points.add(new PortfolioReportChartPoint(
                    firstDate.plusDays(Math.round(totalDays * index / (values.size() - 1d))),
                    new BigDecimal(values.get(index)),
                    new BigDecimal(contributions.get(index))));
        }
        return List.copyOf(points);
    }

    private PortfolioReportMovement movement(
            String type,
            String ticker,
            String name,
            String quantity,
            String total) {
        return new PortfolioReportMovement(
                LocalDate.of(2026, 8, 15),
                type,
                ticker,
                name,
                quantity == null ? null : new BigDecimal(quantity),
                new BigDecimal(total),
                null);
    }

    private PortfolioReportAssetHighlight highlight(String ticker, String name, String rate, String amount) {
        return new PortfolioReportAssetHighlight(
                ticker, name, new BigDecimal(rate), new BigDecimal(amount), null);
    }

    private PortfolioReportAllocation allocation(String key, String name, String rate) {
        return new PortfolioReportAllocation(key, name, new BigDecimal(rate), null);
    }
}
