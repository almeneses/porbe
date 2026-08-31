package com.porbe.app.market;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la única programación de cierres del portafolio. */
public interface MarketDataScheduleRepository extends JpaRepository<MarketDataSchedule, Long> {

    Optional<MarketDataSchedule> findByScheduleKey(String scheduleKey);
}
