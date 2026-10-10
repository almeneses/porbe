package com.porbe.app.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortfolioReportAiGuidanceRequest(@NotBlank @Size(max = 2000) String text) {
}
