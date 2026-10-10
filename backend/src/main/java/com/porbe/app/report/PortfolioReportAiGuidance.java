package com.porbe.app.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Última indicación editorial y reserva breve para una generación del portafolio. */
@Entity
@Table(name = "portfolio_report_ai_guidance")
public class PortfolioReportAiGuidance {
    @Id
    @Column(name = "portfolio_id")
    Long portfolioId;
    @Column(nullable = false, length = 2000)
    String text = "";
    @Column(nullable = false)
    long revision;
    @Column(name = "used_revision", nullable = false)
    long usedRevision;
    @Column(name = "claimed_report_id")
    Long claimedReportId;
    @Column(name = "claimed_at")
    OffsetDateTime claimedAt;
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;

    protected PortfolioReportAiGuidance() { }

    PortfolioReportAiGuidance(Long portfolioId, OffsetDateTime now) {
        this.portfolioId = portfolioId;
        this.updatedAt = now;
    }

    boolean pending() { return !text.isBlank() && revision > usedRevision; }

    void release() {
        claimedReportId = null;
        claimedAt = null;
    }
}
