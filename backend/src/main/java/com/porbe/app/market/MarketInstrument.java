package com.porbe.app.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "market_instrument")
/** Metadatos de un ticker conocido y control de su última sincronización. */
public class MarketInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String ticker;

    @Column(length = 160)
    private String name;

    @Column(length = 3)
    private String currency;

    @Column(name = "exchange_code", length = 30)
    private String exchange;

    @Column(name = "instrument_type", length = 30)
    private String instrumentType;

    @Column(name = "exchange_timezone", length = 80)
    private String exchangeTimezone;

    @Column(length = 80)
    private String sector;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MarketInstrument() {
    }

    public MarketInstrument(String ticker) {
        this.ticker = ticker;
    }

    public void updateFrom(MarketDataSeries series, OffsetDateTime syncedAt) {
        this.name = shorten(series.name(), 160);
        this.currency = shorten(series.currency(), 3);
        this.exchange = shorten(series.exchange(), 30);
        this.instrumentType = shorten(series.instrumentType(), 30);
        this.exchangeTimezone = shorten(series.exchangeTimezone(), 80);
        if (sector == null || sector.isBlank()) {
            this.sector = MarketSectorCatalog.suggestedSector(ticker);
        }
        this.lastSyncedAt = syncedAt;
    }

    public void updateSector(String sector) {
        this.sector = shorten(sector.trim(), 80);
    }

    private String shorten(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public String getExchange() {
        return exchange;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public String getExchangeTimezone() {
        return exchangeTimezone;
    }

    public String getSector() {
        return sector == null || sector.isBlank()
                ? MarketSectorCatalog.suggestedSector(ticker)
                : sector;
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }
}
