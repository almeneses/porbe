package com.porbe.app.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Preferencias editables de la redacción asistida por IA. */
public record PortfolioReportAiSettingsRequest(
        boolean enabled,
        @NotBlank @Size(max = 120) String model,
        @NotBlank @Pattern(regexp = "low|medium|high|xhigh|max|ultra") String effort) {
}
