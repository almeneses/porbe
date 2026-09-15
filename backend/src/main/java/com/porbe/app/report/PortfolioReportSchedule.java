package com.porbe.app.report;

import com.porbe.app.scheduling.WeeklySchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** Configuración persistente de la elaboración y envío del informe semanal. */
@Entity
@Table(name = "portfolio_report_schedule")
public class PortfolioReportSchedule extends WeeklySchedule {

    public static final String WEEKLY_REPORT = "WEEKLY_REPORT";

    @Id
    @Column(name = "schedule_key", nullable = false, length = 40)
    private String scheduleKey;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    @Column(name = "ai_model", nullable = false, length = 120)
    private String aiModel;

    @Column(name = "ai_effort", nullable = false, length = 10)
    private String aiEffort;

    protected PortfolioReportSchedule() {
    }

    PortfolioReportSchedule(DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        super(true, dayOfWeek, runTime, timezone, updatedBy);
        this.scheduleKey = WEEKLY_REPORT;
        this.aiEnabled = true;
        this.aiModel = "gpt-5.4-mini";
        this.aiEffort = "low";
    }

    void updateAi(boolean enabled, String model, String effort, String updatedBy) {
        this.aiEnabled = enabled;
        this.aiModel = model;
        this.aiEffort = effort;
        this.updatedBy = updatedBy;
    }

    void markRunning(OffsetDateTime runAt) {
        super.markRunning(runAt, "Generación automática en curso.");
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getAiEffort() {
        return aiEffort;
    }
}
