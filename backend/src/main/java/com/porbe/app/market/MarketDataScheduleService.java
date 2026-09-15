package com.porbe.app.market;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return toResponse(schedule());
    }

    @Transactional
    public MarketDataScheduleResponse update(MarketDataScheduleRequest request, String username) {
        var schedule = schedule();
        schedule.update(request.enabled(), request.dayOfWeek(), request.runTime(), username);
        return toResponse(repository.save(schedule));
    }

    /** Marca la ejecución antes de salir a Yahoo para impedir reclamos duplicados. */
    @Transactional
    public Optional<Long> claimIfDue() {
        var schedule = schedule();
        var now = clock.instant();
        if (!schedule.isDue(now)) {
            return Optional.empty();
        }
        var zone = ZoneId.of(schedule.getTimezone());
        schedule.markRunning(OffsetDateTime.ofInstant(now, zone));
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

    private MarketDataScheduleResponse toResponse(MarketDataSchedule schedule) {
        return new MarketDataScheduleResponse(
                schedule.isEnabled(),
                schedule.getDayOfWeek(),
                schedule.getRunTime(),
                schedule.getTimezone(),
                schedule.nextRun(clock.instant()),
                schedule.getLastRunAt(),
                schedule.getLastRunStatus(),
                schedule.getLastRunMessage(),
                schedule.getUpdatedBy(),
                schedule.getUpdatedAt());
    }
}
