package com.porbe.app.operation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consulta cronológicamente la bitácora de operaciones. */
public interface OperationAuditRepository extends JpaRepository<OperationAudit, Long> {

    List<OperationAudit> findTop100ByOrderByCreatedAtDescIdDesc();

    List<OperationAudit> findTop100ByPortfolioOrderByCreatedAtDescIdDesc(
            com.porbe.app.portfolio.Portfolio portfolio);
}
