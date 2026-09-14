package com.porbe.app.report;

import java.util.List;

/** Modelo visible en el catálogo de la instalación local de Codex. */
public record PortfolioReportAiModelOption(
        String model,
        String name,
        String defaultEffort,
        List<String> efforts) {
}
