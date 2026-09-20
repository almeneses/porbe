package com.porbe.app.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Estado común de los horarios semanales, persistido en la tabla de cada trabajo. */
@MappedSuperclass
public abstract class WeeklySchedule {

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
    protected String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WeeklySchedule() {
    }

    protected WeeklySchedule(
            boolean enabled, DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        update(enabled, dayOfWeek, runTime, timezone, updatedBy);
    }

    public void update(boolean enabled, DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        this.enabled = enabled;
        this.dayOfWeek = dayOfWeek;
        this.runTime = runTime;
        this.timezone = timezone;
        this.updatedBy = updatedBy;
    }

    public boolean isDue(Instant instant) {
        if (!enabled) {
            return false;
        }
        var zone = ZoneId.of(timezone);
        var now = instant.atZone(zone);
        return now.getDayOfWeek() == dayOfWeek
                && !now.toLocalTime().isBefore(runTime)
                && (lastRunAt == null
                        || !lastRunAt.atZoneSameInstant(zone).toLocalDate().equals(now.toLocalDate()));
    }

    public OffsetDateTime nextRun(Instant instant) {
        if (!enabled) {
            return null;
        }
        var zone = ZoneId.of(timezone);
        var now = instant.atZone(zone);
        var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        var candidate = ZonedDateTime.of(date, runTime, zone);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toOffsetDateTime();
    }

    protected void markRunning(OffsetDateTime runAt, String message) {
        this.lastRunAt = runAt;
        this.lastRunStatus = "RUNNING";
        this.lastRunMessage = message;
    }

    public void markFinished(String status, String message) {
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
