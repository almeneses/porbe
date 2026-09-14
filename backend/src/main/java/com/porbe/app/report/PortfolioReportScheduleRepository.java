package com.porbe.app.report;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persiste la programación semanal del informe. */
public interface PortfolioReportScheduleRepository extends JpaRepository<PortfolioReportSchedule, String> {
}
