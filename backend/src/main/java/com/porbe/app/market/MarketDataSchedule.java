package com.porbe.app.market;

import com.porbe.app.scheduling.WeeklySchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** Configuración persistente del trabajo semanal de actualización de precios. */
@Entity
@Table(name = "market_data_schedule")
public class MarketDataSchedule extends WeeklySchedule {

    public static final String PORTFOLIO_CLOSES = "PORTFOLIO_CLOSES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_key", nullable = false, unique = true, length = 40)
    private String scheduleKey;

    protected MarketDataSchedule() {
    }

    public MarketDataSchedule(DayOfWeek dayOfWeek, LocalTime runTime, String timezone, String updatedBy) {
        super(false, dayOfWeek, runTime, timezone, updatedBy);
        this.scheduleKey = PORTFOLIO_CLOSES;
    }

    public void update(boolean enabled, DayOfWeek dayOfWeek, LocalTime runTime, String updatedBy) {
        super.update(enabled, dayOfWeek, runTime, getTimezone(), updatedBy);
    }

    public void markRunning(OffsetDateTime runAt) {
        super.markRunning(runAt, "Actualización automática en curso.");
    }

    public Long getId() {
        return id;
    }
}
