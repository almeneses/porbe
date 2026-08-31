package com.porbe.app.operation;

import com.porbe.app.importer.ImportBatch;
import com.porbe.app.portfolio.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Movimiento financiero editable que alimenta todos los cálculos del portafolio. */
@Entity
@Table(name = "portfolio_operation")
public class PortfolioOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @Column(name = "operation_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType type;

    @Column(length = 30)
    private String ticker;

    @Column(name = "asset_name", length = 160)
    private String name;

    @Column(precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 24, scale = 8)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal commission;

    @Column(name = "total_amount", nullable = false, precision = 24, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected PortfolioOperation() {
    }

    public PortfolioOperation(
            Portfolio portfolio,
            ImportBatch importBatch,
            LocalDate date,
            OperationType type,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal commission,
            BigDecimal totalAmount,
            String notes) {
        this.portfolio = portfolio;
        this.importBatch = importBatch;
        this.date = date;
        this.type = type;
        this.ticker = ticker;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.commission = commission;
        this.totalAmount = totalAmount;
        this.notes = notes;
        this.updatedBy = importBatch.getImportedBy();
    }

    /** Sustituye los datos financieros conservando el origen y el identificador. */
    public void update(
            LocalDate date,
            OperationType type,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal commission,
            BigDecimal totalAmount,
            String notes,
            String username) {
        this.date = date;
        this.type = type;
        this.ticker = ticker;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.commission = commission;
        this.totalAmount = totalAmount;
        this.notes = notes;
        this.updatedBy = username;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public OperationType getType() {
        return type;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
