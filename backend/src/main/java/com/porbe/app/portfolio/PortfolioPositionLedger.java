package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Acumula la historia contable de un ticker y centraliza el costo promedio
 * utilizado tanto por la valoración actual como por el histórico semanal.
 */
final class PortfolioPositionLedger {

    private static final int CALCULATION_SCALE = 16;

    private final String ticker;
    private String name;
    private BigDecimal netQuantity = BigDecimal.ZERO;
    private BigDecimal accountingQuantity = BigDecimal.ZERO;
    private BigDecimal costBasis = BigDecimal.ZERO;
    private BigDecimal totalPurchases = BigDecimal.ZERO;
    private BigDecimal realizedGain = BigDecimal.ZERO;
    private BigDecimal dividends = BigDecimal.ZERO;
    private boolean calculationComplete = true;

    PortfolioPositionLedger(String ticker) {
        this.ticker = ticker;
    }

    void purchase(BigDecimal quantity, BigDecimal amount) {
        netQuantity = netQuantity.add(quantity);
        totalPurchases = totalPurchases.add(amount);
        if (calculationComplete) {
            accountingQuantity = accountingQuantity.add(quantity);
            costBasis = costBasis.add(amount);
        }
    }

    /** Libera costo promedio y reconoce el resultado realizado de la salida. */
    void sale(BigDecimal quantity, BigDecimal amount) {
        netQuantity = netQuantity.subtract(quantity);
        if (!calculationComplete) {
            return;
        }
        if (accountingQuantity.signum() <= 0 || quantity.compareTo(accountingQuantity) > 0) {
            calculationComplete = false;
            return;
        }
        var averageCost = costBasis.divide(accountingQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
        var releasedCost = averageCost.multiply(quantity);
        realizedGain = realizedGain.add(amount.subtract(releasedCost));
        accountingQuantity = accountingQuantity.subtract(quantity);
        costBasis = accountingQuantity.signum() == 0 ? BigDecimal.ZERO : costBasis.subtract(releasedCost);
    }

    void name(String name) {
        if (name != null && !name.isBlank()) this.name = name;
    }

    void dividend(BigDecimal amount) {
        dividends = dividends.add(amount);
    }

    void incomplete() {
        calculationComplete = false;
    }

    String ticker() {
        return ticker;
    }

    String name() {
        return name;
    }

    BigDecimal netQuantity() {
        return netQuantity;
    }

    BigDecimal costBasis() {
        return costBasis;
    }

    BigDecimal totalPurchases() {
        return totalPurchases;
    }

    BigDecimal realizedGain() {
        return realizedGain;
    }

    BigDecimal dividends() {
        return dividends;
    }

    boolean calculationComplete() {
        return calculationComplete;
    }
}
