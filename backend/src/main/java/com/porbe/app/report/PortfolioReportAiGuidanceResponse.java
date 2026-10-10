package com.porbe.app.report;

import java.time.OffsetDateTime;

public record PortfolioReportAiGuidanceResponse(String text, String status, long revision, OffsetDateTime updatedAt) {
}
