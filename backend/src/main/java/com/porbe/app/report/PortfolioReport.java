package com.porbe.app.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Informe generado y sus artefactos binarios para descarga y entrega posterior. */
@Entity
@Table(name = "portfolio_report")
public class PortfolioReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_from", nullable = false)
    private LocalDate from;

    @Column(name = "report_to", nullable = false)
    private LocalDate to;

    @Column(name = "baseline_date", nullable = false)
    private LocalDate baselineDate;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "generated_by", nullable = false, length = 120)
    private String generatedBy;

    @Column(name = "valuation_complete", nullable = false)
    private boolean valuationComplete;

    @Column(name = "provisional_prices", nullable = false)
    private int provisionalPrices;

    @Column(name = "image_data")
    private byte[] imageData;

    @Column(name = "pdf_data")
    private byte[] pdfData;

    @Column(name = "image_size", nullable = false)
    private int imageSize;

    @Column(name = "pdf_size", nullable = false)
    private int pdfSize;

    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus;

    @Column(name = "delivery_message", length = 500)
    private String deliveryMessage;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PortfolioReport() {
    }

    public PortfolioReport(PortfolioReportData data, String triggerType, String generatedBy) {
        this.from = data.from();
        this.to = data.to();
        this.baselineDate = data.baselineDate();
        this.valuationDate = data.valuationDate();
        this.baseCurrency = data.baseCurrency();
        this.triggerType = triggerType;
        this.status = "GENERATING";
        this.generatedBy = generatedBy;
        this.valuationComplete = data.valuationComplete();
        this.provisionalPrices = data.provisionalPrices();
        this.imageSize = 0;
        this.pdfSize = 0;
        this.deliveryStatus = "NOT_CONFIGURED";
        this.deliveryMessage = "WhatsApp Business se configurará en un incremento posterior.";
    }

    public void markReady(byte[] imageData, byte[] pdfData, OffsetDateTime generatedAt) {
        this.imageData = imageData;
        this.pdfData = pdfData;
        this.imageSize = imageData.length;
        this.pdfSize = pdfData.length;
        this.generatedAt = generatedAt;
        this.status = "READY";
        this.errorMessage = null;
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = shorten(message, 1000);
    }

    public void markDelivery(String status, String message) {
        this.deliveryStatus = status;
        this.deliveryMessage = shorten(message, 500);
    }

    private String shorten(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    public Long getId() { return id; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public LocalDate getBaselineDate() { return baselineDate; }
    public LocalDate getValuationDate() { return valuationDate; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getTriggerType() { return triggerType; }
    public String getStatus() { return status; }
    public String getGeneratedBy() { return generatedBy; }
    public boolean isValuationComplete() { return valuationComplete; }
    public int getProvisionalPrices() { return provisionalPrices; }
    public byte[] getImageData() { return imageData; }
    public byte[] getPdfData() { return pdfData; }
    public int getImageSize() { return imageSize; }
    public int getPdfSize() { return pdfSize; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public String getDeliveryMessage() { return deliveryMessage; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
