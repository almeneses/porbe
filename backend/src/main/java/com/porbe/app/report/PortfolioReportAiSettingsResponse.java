package com.porbe.app.report;

import java.util.List;

/** Configuración de IA y opciones que la CLI de Codex ofrece actualmente. */
public record PortfolioReportAiSettingsResponse(
        boolean enabled,
        String model,
        String effort,
        boolean catalogAvailable,
        List<PortfolioReportAiModelOption> models) {
}
