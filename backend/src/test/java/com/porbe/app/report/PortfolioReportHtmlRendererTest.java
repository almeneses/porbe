package com.porbe.app.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifica que Thymeleaf resuelva la plantilla sin dejar expresiones en el HTML final. */
@SpringBootTest
class PortfolioReportHtmlRendererTest {

    @Autowired private PortfolioReportHtmlRenderer renderer;

    @Test
    void rendersSelfContainedSpanishHtml() {
        var point = new PortfolioReportChartPoint(
                LocalDate.of(2026, 1, 16), new BigDecimal("2200"), new BigDecimal("2000"));
        var data = new PortfolioReportData(
                1L,
                "Portafolio de prueba",
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 16),
                "COP",
                new BigDecimal("200"),
                new BigDecimal("0.10"),
                highlight("ECOPETROL.CL", "Ecopetrol", "0.10", "180"),
                highlight("PFBCOLOM.CL", "Bancolombia preferencial", "-0.02", "-20"),
                new BigDecimal("20"),
                new BigDecimal("220"),
                new BigDecimal("0.11"),
                new BigDecimal("0.09"),
                new BigDecimal("2000"),
                new BigDecimal("1000"),
                new BigDecimal("2200"),
                1,
                List.of(new PortfolioReportMovement(
                        LocalDate.of(2026, 1, 5), "compra", "ECOPETROL.CL", "Ecopetrol",
                        BigDecimal.TEN, new BigDecimal("1000"), null)),
                List.of(point),
                List.of(new PortfolioReportAssetValue("ECOPETROL.CL", "Ecopetrol", new BigDecimal("200"))),
                List.of(new PortfolioReportAssetValue("ECOPETROL.CL", "Ecopetrol", new BigDecimal("20"))),
                List.of(new PortfolioReportAllocation(
                        "ECOPETROL.CL", "Ecopetrol", new BigDecimal("0.75"), null)),
                List.of(new PortfolioReportAllocation(
                        "Energía", "Energía", new BigDecimal("0.75"), null)),
                true,
                0,
                0);

        var html = renderer.render(data);

        assertThat(html)
                .contains("<article class=\"report\" id=\"portfolio-report\">")
                .contains("PORTAFOLIO DE PRUEBA")
                .contains("Cómo ha cambiado tu portafolio")
                .contains("La que más ayudó")
                .contains("Ganancias por acción")
                .contains("Dividendos por acción")
                .contains("Rendimiento total")
                .contains("¿Dónde está invertido tu dinero?")
                .contains("aria-label=\"Composición por acción\"")
                .contains("class=\"donut-percentage\"")
                .contains("class=\"chart-end-label portfolio\"")
                .contains("<strong>Ecopetrol</strong>")
                .contains(".report-header")
                .contains("<svg")
                .doesNotContain("Por tipo de empresa")
                .doesNotContain("class=\"impact-ticker\"")
                .doesNotContain("TWR", "últimos 6 meses")
                .doesNotContain("COMENTARIO DE PRUEBA")
                .doesNotContain("th:text", "th:if", "th:utext", "${report");
    }

    @Test
    void rendersAnOptionalPlainLanguageNote() {
        var point = new PortfolioReportChartPoint(
                LocalDate.of(2026, 1, 16), new BigDecimal("2200"), new BigDecimal("2000"));
        var data = new PortfolioReportData(
                1L, "Portafolio de prueba", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 16), "COP",
                new BigDecimal("200"), new BigDecimal("0.10"),
                highlight("ECOPETROL.CL", "Ecopetrol", "0.10", "180"), null,
                new BigDecimal("20"), new BigDecimal("220"), new BigDecimal("0.11"),
                new BigDecimal("0.09"), new BigDecimal("2000"), new BigDecimal("1000"), new BigDecimal("2200"),
                0, List.of(), List.of(point), List.of(), List.of(), List.of(), List.of(), true, 0, 0);

        var html = renderer.render(data, new PortfolioReportTemplateModel.Note(
                "Lo más importante de este periodo",
                "Tu portafolio creció y recibió dividendos.",
                "Mantener una mezcla de empresas ayuda a repartir el riesgo."));

        assertThat(html)
                .contains("COMENTARIO DEL PERIODO")
                .contains("Lo más importante de este periodo")
                .contains("Tu portafolio creció y recibió dividendos.")
                .contains("Mantener una mezcla de empresas ayuda a repartir el riesgo.")
                .doesNotContain("th:text", "th:if", "${report");
    }

    private PortfolioReportAssetHighlight highlight(String ticker, String name, String rate, String amount) {
        return new PortfolioReportAssetHighlight(
                ticker, name, new BigDecimal(rate), new BigDecimal(amount), null);
    }
}
