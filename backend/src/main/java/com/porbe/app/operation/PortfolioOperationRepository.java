package com.porbe.app.operation;

import com.porbe.app.portfolio.Portfolio;
import com.porbe.app.importer.ImportBatch;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso al libro de operaciones y sus rangos de cobertura por ticker. */
public interface PortfolioOperationRepository extends JpaRepository<PortfolioOperation, Long> {

    List<PortfolioOperation> findTop200ByOrderByDateDescIdDesc();

    List<PortfolioOperation> findAllByPortfolioOrderByDateAscIdAsc(Portfolio portfolio);

    java.util.Optional<PortfolioOperation> findByIdAndPortfolio(Long id, Portfolio portfolio);

    List<PortfolioOperation> findAllByPortfolioAndDateBetweenOrderByDateAscIdAsc(
            Portfolio portfolio,
            LocalDate from,
            LocalDate to);

    List<PortfolioOperation> findAllByImportBatchOrderByDateAscIdAsc(ImportBatch importBatch);

    long countByImportBatch(ImportBatch importBatch);

    void deleteAllByImportBatch(ImportBatch importBatch);

    @Query("""
            SELECT UPPER(operation.ticker) AS ticker, MIN(operation.date) AS firstOperationDate
            FROM PortfolioOperation operation
            WHERE operation.ticker IS NOT NULL
            GROUP BY UPPER(operation.ticker)
            ORDER BY UPPER(operation.ticker)
            """)
    List<PortfolioTickerRange> findPortfolioTickerRanges();

    @Query("""
            SELECT UPPER(operation.ticker) AS ticker, MIN(operation.date) AS firstOperationDate
            FROM PortfolioOperation operation
            WHERE operation.portfolio = :portfolio AND operation.ticker IS NOT NULL
            GROUP BY UPPER(operation.ticker)
            ORDER BY UPPER(operation.ticker)
            """)
    List<PortfolioTickerRange> findPortfolioTickerRanges(Portfolio portfolio);
}
