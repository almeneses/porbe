package com.porbe.app.importer;

import com.porbe.app.portfolio.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "import_batch")
/** Registra la identidad y el resultado de cada archivo importado. */
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "source_filename", nullable = false, length = 255)
    private String sourceFilename;

    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private OffsetDateTime importedAt;

    protected ImportBatch() {
    }

    public ImportBatch(
            Portfolio portfolio,
            String sourceFilename,
            String fileHash,
            int rowCount,
            String importedBy) {
        this.portfolio = portfolio;
        this.sourceFilename = sourceFilename;
        this.fileHash = fileHash;
        this.rowCount = rowCount;
        this.importedBy = importedBy;
    }

    public Long getId() {
        return id;
    }
}
