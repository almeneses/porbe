package com.porbe.app.market;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administra el horario y evita ejecutar dos veces el trabajo de una semana. */
@Service
public class MarketDataScheduleService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Bogota");

    private final MarketDataScheduleRepository repository;
    private final Clock clock;

    public MarketDataScheduleService(MarketDataScheduleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public MarketDataScheduleResponse current() {
        return response(schedule());
    }

    @Transactional
    public MarketDataScheduleResponse update(MarketDataScheduleRequest request, String username) {
        var schedule = schedule();
        schedule.update(request.enabled(), request.dayOfWeek(), request.runTime(), username);
        return response(repository.save(schedule));
    }

    /** Marca la ejecución antes de salir a Yahoo para impedir reclamos duplicados. */
    @Transactional
    public Optional<Long> claimIfDue() {
        var schedule = schedule();
        if (!schedule.isEnabled()) {
            return Optional.empty();
        }
        var zone = ZoneId.of(schedule.getTimezone());
        var now = ZonedDateTime.ofInstant(clock.instant(), zone);
        if (now.getDayOfWeek() != schedule.getDayOfWeek()
                || now.toLocalTime().isBefore(schedule.getRunTime())) {
            return Optional.empty();
        }
        if (schedule.getLastRunAt() != null
                && schedule.getLastRunAt().atZoneSameInstant(zone).toLocalDate().equals(now.toLocalDate())) {
            return Optional.empty();
        }
        schedule.markRunning(OffsetDateTime.ofInstant(clock.instant(), zone));
        return Optional.of(repository.save(schedule).getId());
    }

    @Transactional
    public void finish(Long scheduleId, String status, String message) {
        repository.findById(scheduleId).ifPresent(schedule -> schedule.markFinished(status, message));
    }

    private MarketDataSchedule schedule() {
        return repository.findByScheduleKey(MarketDataSchedule.PORTFOLIO_CLOSES)
                .orElseGet(() -> repository.save(new MarketDataSchedule(
                        DayOfWeek.SATURDAY,
                        LocalTime.of(8, 0),
                        DEFAULT_ZONE.getId(),
                        "system")));
    }

    private MarketDataScheduleResponse response(MarketDataSchedule schedule) {
        return new MarketDataScheduleResponse(
                schedule.isEnabled(),
                schedule.getDayOfWeek(),
                schedule.getRunTime(),
                schedule.getTimezone(),
                schedule.isEnabled() ? nextRun(schedule) : null,
                schedule.getLastRunAt(),
                schedule.getLastRunStatus(),
                schedule.getLastRunMessage(),
                schedule.getUpdatedBy(),
                schedule.getUpdatedAt());
    }

    private OffsetDateTime nextRun(MarketDataSchedule schedule) {
        var zone = ZoneId.of(schedule.getTimezone());
        var now = ZonedDateTime.ofInstant(clock.instant(), zone);
        var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(schedule.getDayOfWeek()));
        var candidate = ZonedDateTime.of(date, schedule.getRunTime(), zone);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toOffsetDateTime();
    }
}
