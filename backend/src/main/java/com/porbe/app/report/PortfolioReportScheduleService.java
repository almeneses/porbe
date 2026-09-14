package com.porbe.app.report;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administra el horario persistido y evita ejecutar dos veces el informe en un mismo día. */
@Service
public class PortfolioReportScheduleService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PortfolioReportScheduleRepository repository;
    private final PortfolioReportDeliveryProvider deliveryProvider;
    private final Clock clock;

    public PortfolioReportScheduleService(
            PortfolioReportScheduleRepository repository,
            PortfolioReportDeliveryProvider deliveryProvider,
            Clock clock) {
        this.repository = repository;
        this.deliveryProvider = deliveryProvider;
        this.clock = clock;
    }

    @Transactional
    public PortfolioReportScheduleResponse current() {
        return response(schedule());
    }

    @Transactional
    public PortfolioReportScheduleResponse update(PortfolioReportScheduleRequest request, String username) {
        var timezone = validTimezone(request.timezone());
        var schedule = schedule();
        schedule.update(request.enabled(), request.dayOfWeek(), request.runTime(), timezone.getId(), username);
        return response(repository.save(schedule));
    }

    @Transactional
    public PortfolioReportAiSettings aiSettings() {
        return aiSettings(schedule());
    }

    @Transactional
    public PortfolioReportAiSettings updateAi(PortfolioReportAiSettingsRequest request, String username) {
        var schedule = schedule();
        schedule.updateAi(request.enabled(), request.model().trim(), request.effort(), username);
        return aiSettings(repository.save(schedule));
    }

    /** Marca la ejecución antes de generar artefactos para impedir reclamos duplicados. */
    @Transactional
    public Optional<ZoneId> claimIfDue() {
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
        repository.save(schedule);
        return Optional.of(zone);
    }

    @Transactional
    public void finish(String status, String message) {
        repository.findById(PortfolioReportSchedule.WEEKLY_REPORT)
                .ifPresent(schedule -> schedule.markFinished(status, message));
    }

    private PortfolioReportSchedule schedule() {
        return repository.findById(PortfolioReportSchedule.WEEKLY_REPORT)
                .orElseGet(() -> repository.save(new PortfolioReportSchedule(
                        DayOfWeek.FRIDAY,
                        LocalTime.of(17, 30),
                        DEFAULT_ZONE.getId(),
                        "system")));
    }

    private PortfolioReportScheduleResponse response(PortfolioReportSchedule schedule) {
        return new PortfolioReportScheduleResponse(
                schedule.isEnabled(),
                schedule.getDayOfWeek(),
                schedule.getRunTime().format(TIME_FORMAT),
                schedule.getTimezone(),
                schedule.isEnabled() ? nextRun(schedule) : null,
                schedule.getLastRunAt(),
                schedule.getLastRunStatus(),
                schedule.getLastRunMessage(),
                schedule.getUpdatedBy(),
                schedule.getUpdatedAt(),
                deliveryProvider.configured(),
                deliveryProvider.channel());
    }

    private PortfolioReportAiSettings aiSettings(PortfolioReportSchedule schedule) {
        return new PortfolioReportAiSettings(
                schedule.isAiEnabled(),
                schedule.getAiModel(),
                schedule.getAiEffort());
    }

    private OffsetDateTime nextRun(PortfolioReportSchedule schedule) {
        var zone = ZoneId.of(schedule.getTimezone());
        var now = ZonedDateTime.ofInstant(clock.instant(), zone);
        var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(schedule.getDayOfWeek()));
        var candidate = ZonedDateTime.of(date, schedule.getRunTime(), zone);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toOffsetDateTime();
    }

    private ZoneId validTimezone(String value) {
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Selecciona una zona horaria válida.");
        }
    }
}
