package com.porbe.app.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Configuración persistente de la elaboración y envío del informe semanal. */
@Entity
@Table(name = "portfolio_report_schedule")
public class PortfolioReportSchedule {

    public static final String WEEKLY_REPORT = "WEEKLY_REPORT";

    @Id
    @Column(name = "schedule_key", nullable = false, length = 40)
    private String scheduleKey;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "run_time", nullable = false)
    private LocalTime runTime;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    @Column(name = "ai_model", nullable = false, length = 120)
    private String aiModel;

    @Column(name = "ai_effort", nullable = false, length = 10)
    private String aiEffort;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    @Column(name = "last_run_message", length = 500)
    private String lastRunMessage;

    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PortfolioReportSchedule() {
    }

    PortfolioReportSchedule(DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        this.scheduleKey = WEEKLY_REPORT;
        this.enabled = true;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime;
        this.timezone = timezone;
        this.aiEnabled = true;
        this.aiModel = "gpt-5.4-mini";
        this.aiEffort = "low";
        this.updatedBy = updatedBy;
    }

    void update(boolean enabled, DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        this.enabled = enabled;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime;
        this.timezone = timezone;
        this.updatedBy = updatedBy;
    }

    void updateAi(boolean enabled, String model, String effort, String updatedBy) {
        this.aiEnabled = enabled;
        this.aiModel = model;
        this.aiEffort = effort;
        this.updatedBy = updatedBy;
    }

    void markRunning(OffsetDateTime runAt) {
        this.lastRunAt = runAt;
        this.lastRunStatus = "RUNNING";
        this.lastRunMessage = "Generación automática en curso.";
    }

    void markFinished(String status, String message) {
        this.lastRunStatus = status;
        this.lastRunMessage = message == null || message.length() <= 500 ? message : message.substring(0, 500);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getRunTime() {
        return runTime;
    }

    public String getTimezone() {
        return timezone;
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

    public OffsetDateTime getLastRunAt() {
        return lastRunAt;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public String getLastRunMessage() {
        return lastRunMessage;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
