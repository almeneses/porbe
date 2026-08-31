package com.porbe.app.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a informes sin cargar sus binarios al construir el historial. */
public interface PortfolioReportRepository extends JpaRepository<PortfolioReport, Long> {

    @Query("""
            select new com.porbe.app.report.PortfolioReportListItem(
                report.id, report.from, report.to, report.valuationDate,
                report.baseCurrency, report.triggerType, report.status,
                report.generatedBy, report.valuationComplete, report.provisionalPrices,
                report.imageSize, report.pdfSize, report.deliveryStatus,
                report.deliveryMessage, report.errorMessage, report.generatedAt, report.createdAt)
            from PortfolioReport report
            order by report.createdAt desc
            limit 100
            """)
    List<PortfolioReportListItem> listRecent();

    Optional<PortfolioReport> findFirstByFromAndToAndTriggerTypeAndStatusOrderByCreatedAtDesc(
            LocalDate from,
            LocalDate to,
            String triggerType,
            String status);
}
