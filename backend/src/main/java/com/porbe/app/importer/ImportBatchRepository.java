package com.porbe.app.importer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los lotes de importación y detección de archivos repetidos. */
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    boolean existsByPortfolioAndFileHash(com.porbe.app.portfolio.Portfolio portfolio, String fileHash);

    List<ImportBatch> findAllBySourceTypeOrderByImportedAtDesc(ImportSourceType sourceType);

    List<ImportBatch> findAllByPortfolioAndSourceTypeOrderByImportedAtDesc(
            com.porbe.app.portfolio.Portfolio portfolio,
            ImportSourceType sourceType);
}
