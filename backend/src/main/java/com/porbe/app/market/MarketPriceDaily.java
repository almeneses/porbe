package com.porbe.app.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "market_price_daily")
/** Cotización OHLCV diaria persistida para un instrumento. */
public class MarketPriceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "open_price", precision = 24, scale = 8)
    private BigDecimal open;

    @Column(name = "high_price", precision = 24, scale = 8)
    private BigDecimal high;

    @Column(name = "low_price", precision = 24, scale = 8)
    private BigDecimal low;

    @Column(name = "close_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal close;

    @Column(name = "adjusted_close", precision = 24, scale = 8)
    private BigDecimal adjustedClose;

    private Long volume;

    @Column(name = "final_close", nullable = false)
    private boolean finalClose;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    protected MarketPriceDaily() {
    }

    public MarketPriceDaily(MarketInstrument instrument, DailyMarketBar bar, String source, OffsetDateTime fetchedAt) {
        this.instrument = instrument;
        this.priceDate = bar.date();
        update(bar, source, fetchedAt);
    }

    public void update(DailyMarketBar bar, String source, OffsetDateTime fetchedAt) {
        this.open = bar.open();
        this.high = bar.high();
        this.low = bar.low();
        this.close = bar.close();
        this.adjustedClose = bar.adjustedClose();
        this.volume = bar.volume();
        this.finalClose = bar.finalClose();
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getAdjustedClose() {
        return adjustedClose;
    }

    public Long getVolume() {
        return volume;
    }

    public boolean isFinalClose() {
        return finalClose;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }
}
