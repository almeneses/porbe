package com.porbe.app.portfolio;

import com.porbe.app.operation.PortfolioOperation;
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

    /** Aplica una operación y libera costo promedio cuando se registra una venta. */
    void apply(PortfolioOperation operation) {
        if (operation.getName() != null && !operation.getName().isBlank()) {
            name = operation.getName();
        }
        switch (operation.getType()) {
            case COMPRA -> applyPurchase(operation);
            case VENTA -> applySale(operation);
            case DIVIDENDO -> dividends = dividends.add(operation.getTotalAmount());
            case DEPOSITO, RETIRO -> {
                // Los movimientos de caja no pertenecen a una posición por ticker.
            }
        }
    }

    private void applyPurchase(PortfolioOperation operation) {
        netQuantity = netQuantity.add(operation.getQuantity());
        totalPurchases = totalPurchases.add(operation.getTotalAmount());
        if (calculationComplete) {
            accountingQuantity = accountingQuantity.add(operation.getQuantity());
            costBasis = costBasis.add(operation.getTotalAmount());
        }
    }

    private void applySale(PortfolioOperation operation) {
        netQuantity = netQuantity.subtract(operation.getQuantity());
        if (!calculationComplete) {
            return;
        }
        if (accountingQuantity.signum() <= 0 || operation.getQuantity().compareTo(accountingQuantity) > 0) {
            calculationComplete = false;
            return;
        }
        var averageCost = costBasis.divide(accountingQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
        var releasedCost = averageCost.multiply(operation.getQuantity());
        realizedGain = realizedGain.add(operation.getTotalAmount().subtract(releasedCost));
        accountingQuantity = accountingQuantity.subtract(operation.getQuantity());
        costBasis = accountingQuantity.signum() == 0 ? BigDecimal.ZERO : costBasis.subtract(releasedCost);
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
