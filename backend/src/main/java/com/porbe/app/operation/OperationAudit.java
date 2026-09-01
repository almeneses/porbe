package com.porbe.app.operation;

import com.porbe.app.portfolio.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/** Conserva una evidencia mínima de cada alta, corrección o eliminación. */
@Entity
@Table(name = "operation_audit")
public class OperationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationAuditAction action;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(name = "previous_data", columnDefinition = "text")
    private String previousData;

    @Column(name = "new_data", columnDefinition = "text")
    private String newData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OperationAudit() {
    }

    public OperationAudit(
            Portfolio portfolio,
            Long operationId,
            Long importBatchId,
            OperationAuditAction action,
            String username,
            String details,
            String previousData,
            String newData) {
        this.portfolio = portfolio;
        this.operationId = operationId;
        this.importBatchId = importBatchId;
        this.action = action;
        this.username = username;
        this.details = details;
        this.previousData = previousData;
        this.newData = newData;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Long getOperationId() {
        return operationId;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public OperationAuditAction getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public String getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
