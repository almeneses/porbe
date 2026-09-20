package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Fórmulas de rendimiento sin acceso a datos; las tasas son decimales, no porcentajes. */
public final class PortfolioPerformanceCalculator {

    private static final int RATE_SCALE = 8;

    private PortfolioPerformanceCalculator() {
    }

    public static BigDecimal nominalGain(BigDecimal start, BigDecimal end, BigDecimal externalCashFlow) {
        return end.subtract(start).subtract(externalCashFlow);
    }

    /** Factor de un tramo sin flujos. null significa que no hay una base de cálculo válida. */
    static BigDecimal growthFactor(BigDecimal start, BigDecimal end) {
        if (start == null || end == null || start.signum() < 0 || end.signum() < 0) {
            return null;
        }
        if (start.signum() == 0) {
            // Un tramo vacío es neutro; ganar valor sin capital inicial no tiene tasa definida.
            return end.signum() == 0 ? BigDecimal.ONE : null;
        }
        return end.divide(start, MathContext.DECIMAL128);
    }

    static BigDecimal compoundGrowth(BigDecimal accumulated, BigDecimal next) {
        return accumulated == null || next == null ? null : accumulated.multiply(next, MathContext.DECIMAL128);
    }

    static BigDecimal returnFromGrowth(BigDecimal growth) {
        return growth == null ? null : growth.subtract(BigDecimal.ONE).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Enlaza tasas del intervalo directamente, incluso después de una pérdida total anterior. */
    public static BigDecimal compoundReturns(List<BigDecimal> returns) {
        if (returns.isEmpty()) {
            return null;
        }
        var growth = BigDecimal.ONE;
        for (var rate : returns) {
            growth = compoundGrowth(growth, rate == null ? null : BigDecimal.ONE.add(rate));
        }
        return returnFromGrowth(growth);
    }

    static BigDecimal annualizedReturn(BigDecimal growth, LocalDate start, LocalDate end) {
        var days = ChronoUnit.DAYS.between(start, end);
        if (days <= 0 || growth == null || growth.signum() < 0) {
            return null;
        }
        var annualized = Math.pow(growth.doubleValue(), 365.0 / days) - 1.0;
        return Double.isFinite(annualized)
                ? BigDecimal.valueOf(annualized).setScale(RATE_SCALE, RoundingMode.HALF_UP)
                : null;
    }
}
