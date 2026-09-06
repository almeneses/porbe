package com.porbe.app.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Procesa la plantilla editable de Thymeleaf con un modelo listo para presentación. */
@Component
public class PortfolioReportHtmlRenderer {

    private static final Locale SPANISH_COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final ClassPathResource REPORT_STYLES =
            new ClassPathResource("static/reports/portfolio-report.css");

    private final SpringTemplateEngine templateEngine;
    private final PortfolioReportTemplateModelFactory modelFactory;

    public PortfolioReportHtmlRenderer(
            SpringTemplateEngine templateEngine,
            PortfolioReportTemplateModelFactory modelFactory) {
        this.templateEngine = templateEngine;
        this.modelFactory = modelFactory;
    }

    public String render(PortfolioReportData data) {
        return render(data, null);
    }

    /** Renderiza una nota opcional, útil para vistas previas y futuros resúmenes automáticos. */
    public String render(PortfolioReportData data, PortfolioReportTemplateModel.Note note) {
        var context = new Context(SPANISH_COLOMBIA);
        context.setVariable("report", modelFactory.create(data, note));
        context.setVariable("reportStyles", reportStyles());
        return templateEngine.process("reports/portfolio-report", context);
    }

    /** Lee el CSS en cada render para que los ajustes locales se reflejen sin recompilar. */
    private String reportStyles() {
        try {
            return REPORT_STYLES.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible cargar los estilos del informe.", exception);
        }
    }
}
