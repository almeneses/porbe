package com.porbe.app.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Configuración persistente del trabajo semanal de actualización de precios. */
@Entity
@Table(name = "market_data_schedule")
public class MarketDataSchedule {

    public static final String PORTFOLIO_CLOSES = "PORTFOLIO_CLOSES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_key", nullable = false, unique = true, length = 40)
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

    protected MarketDataSchedule() {
    }

    public MarketDataSchedule(DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        this.scheduleKey = PORTFOLIO_CLOSES;
        this.enabled = false;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime;
        this.timezone = timezone;
        this.updatedBy = updatedBy;
    }

    public void update(boolean enabled, DayOfWeek dayOfWeek, LocalTime runTime, String updatedBy) {
        this.enabled = enabled;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime;
        this.updatedBy = updatedBy;
    }

    public void markRunning(OffsetDateTime runAt) {
        this.lastRunAt = runAt;
        this.lastRunStatus = "RUNNING";
        this.lastRunMessage = "Actualización automática en curso.";
    }

    public void markFinished(String status, String message) {
        this.lastRunStatus = status;
        this.lastRunMessage = shorten(message, 500);
    }

    private String shorten(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    public Long getId() {
        return id;
    }

    public String getScheduleKey() {
        return scheduleKey;
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
