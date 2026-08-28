package com.porbe.app.importer;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los lotes de importación y detección de archivos repetidos. */
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    boolean existsByFileHash(String fileHash);
}
