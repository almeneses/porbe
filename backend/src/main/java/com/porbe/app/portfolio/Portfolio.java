package com.porbe.app.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Portafolio raíz al que pertenecen los movimientos de inversión. */
@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Portfolio() {
    }

    public Portfolio(String name, String baseCurrency) {
        this.name = name;
        this.baseCurrency = baseCurrency;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    /** Permite renombrar el portafolio sin alterar sus operaciones ni informes previos. */
    public void rename(String name) {
        this.name = name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
