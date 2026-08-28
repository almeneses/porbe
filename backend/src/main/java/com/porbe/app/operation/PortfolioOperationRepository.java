package com.porbe.app.operation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso al libro de operaciones y sus rangos de cobertura por ticker. */
public interface PortfolioOperationRepository extends JpaRepository<PortfolioOperation, Long> {

    List<PortfolioOperation> findTop200ByOrderByDateDescIdDesc();

    @Query("""
            SELECT UPPER(operation.ticker) AS ticker, MIN(operation.date) AS firstOperationDate
            FROM PortfolioOperation operation
            WHERE operation.ticker IS NOT NULL
            GROUP BY UPPER(operation.ticker)
            ORDER BY UPPER(operation.ticker)
            """)
    List<PortfolioTickerRange> findPortfolioTickerRanges();
}
