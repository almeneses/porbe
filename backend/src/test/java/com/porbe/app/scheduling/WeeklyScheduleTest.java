package com.porbe.app.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.porbe.app.market.MarketDataSchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class WeeklyScheduleTest {

    @Test
    void respectsLocalDayTimeAndPreviousRun() {
        var schedule = new MarketDataSchedule(
                DayOfWeek.FRIDAY, LocalTime.of(23, 30), "America/Bogota", "test");
        var due = Instant.parse("2026-09-19T04:30:00Z");
        assertThat(schedule.isDue(due)).isFalse();
        assertThat(schedule.nextRun(due)).isNull();

        schedule.update(true, DayOfWeek.FRIDAY, LocalTime.of(23, 30), "test");
        assertThat(schedule.isDue(due.minusSeconds(1))).isFalse();
        assertThat(schedule.isDue(due)).isTrue();
        assertThat(schedule.isDue(due.plusSeconds(3600))).isFalse();
        assertThat(schedule.nextRun(due.minusSeconds(1)))
                .isEqualTo(OffsetDateTime.parse("2026-09-18T23:30:00-05:00"));
        assertThat(schedule.nextRun(due))
                .isEqualTo(OffsetDateTime.parse("2026-09-25T23:30:00-05:00"));

        schedule.markRunning(OffsetDateTime.parse("2026-09-19T04:30:00Z"));
        assertThat(schedule.getLastRunMessage()).isEqualTo("Actualización automática en curso.");
        schedule.markFinished("SUCCESS", "x".repeat(501));
        assertThat(schedule.getLastRunMessage()).hasSize(500);
        assertThat(schedule.isDue(due.plusSeconds(60))).isFalse();
        assertThat(schedule.isDue(due.plusSeconds(7 * 24 * 3600))).isTrue();
    }

    @Test
    void keepsLocalRunTimeAcrossDaylightSavingChange() {
        var schedule = new MarketDataSchedule(
                DayOfWeek.SUNDAY, LocalTime.of(8, 0), "America/New_York", "test");
        schedule.update(true, DayOfWeek.SUNDAY, LocalTime.of(8, 0), "test");

        assertThat(schedule.nextRun(Instant.parse("2026-03-01T13:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-03-08T08:00:00-04:00"));
        assertThat(schedule.isDue(Instant.parse("2026-03-08T11:59:59Z"))).isFalse();
        assertThat(schedule.isDue(Instant.parse("2026-03-08T12:00:00Z"))).isTrue();
    }
}
