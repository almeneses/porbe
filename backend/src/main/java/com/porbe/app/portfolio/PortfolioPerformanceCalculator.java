package com.porbe.app.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

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

    /** Flujos del inversor: aportes negativos, retiros y valoración final positivos. */
    record CashFlow(LocalDate date, BigDecimal amount) { }

    /** TIR con fechas reales (base 365); devuelve null si no existe una tasa única. */
    static BigDecimal moneyWeightedReturn(
            LocalDate start, BigDecimal initialValue, List<CashFlow> flows,
            LocalDate end, BigDecimal finalValue, boolean annualized) {
        long days = ChronoUnit.DAYS.between(start, end);
        if (days <= 0 || initialValue == null || finalValue == null
                || initialValue.signum() < 0 || finalValue.signum() < 0) return null;
        var amounts = new TreeMap<LocalDate, BigDecimal>();
        amounts.put(start, initialValue.negate());
        for (var flow : flows) {
            if (flow.date().isBefore(start) || flow.date().isAfter(end)) return null;
            amounts.merge(flow.date(), flow.amount(), BigDecimal::add);
        }
        amounts.merge(end, finalValue, BigDecimal::add);
        amounts.values().removeIf(amount -> amount.signum() == 0);
        if (amounts.isEmpty() || amounts.firstEntry().getValue().signum() >= 0) return null;
        if (amounts.values().stream().noneMatch(amount -> amount.signum() > 0)) {
            return finalValue.signum() == 0 && amounts.firstKey().isBefore(end)
                    ? BigDecimal.ONE.negate() : null;
        }
        var coefficients = new double[amounts.size()];
        var exponents = new double[amounts.size()];
        int i = 0;
        for (var entry : amounts.descendingMap().entrySet()) {
            coefficients[i] = entry.getValue().doubleValue();
            exponents[i++] = (double) ChronoUnit.DAYS.between(entry.getKey(), amounts.lastKey()) / days;
        }
        var roots = cashFlowRoots(coefficients, exponents);
        if (roots.size() != 1) return null;
        double result = Math.expm1(roots.getFirst() * (annualized ? 365.0 / days : 1.0));
        return Double.isFinite(result)
                ? BigDecimal.valueOf(result).setScale(RATE_SCALE, RoundingMode.HALF_UP) : null;
    }

    /** Separa tramos monótonos por sus extremos para detectar TIR múltiples, sin elegir una arbitraria. */
    private static List<Double> cashFlowRoots(double[] coefficients, double[] exponents) {
        if (coefficients.length < 2) return List.of();
        if (Arrays.stream(coefficients).allMatch(value -> value >= 0)
                || Arrays.stream(coefficients).allMatch(value -> value <= 0)) return List.of();
        var derivative = new double[coefficients.length - 1];
        var powers = new double[derivative.length];
        for (int i = 0; i < derivative.length; i++) {
            derivative[i] = coefficients[i + 1] * exponents[i + 1];
            powers[i] = exponents[i + 1] - exponents[1];
        }
        // ponytail: limita el factor del periodo a exp(±32); fuera de ese rango se muestra N/D.
        var boundaries = new ArrayList<Double>();
        boundaries.add(-32.0);
        boundaries.addAll(cashFlowRoots(derivative, powers));
        boundaries.add(32.0);
        var roots = new ArrayList<Double>();
        double left = boundaries.getFirst();
        double leftValue = cashFlowValue(coefficients, exponents, left);
        for (int i = 1; i < boundaries.size(); i++) {
            double right = boundaries.get(i);
            double rightValue = cashFlowValue(coefficients, exponents, right);
            if (leftValue == 0) roots.add(left);
            if (Math.signum(leftValue) * Math.signum(rightValue) < 0) {
                double low = left, high = right;
                for (int iteration = 0; iteration < 100; iteration++) {
                    double middle = (low + high) / 2;
                    double value = cashFlowValue(coefficients, exponents, middle);
                    if (value == 0) { low = high = middle; break; }
                    if (Math.signum(value) == Math.signum(leftValue)) low = middle;
                    else high = middle;
                }
                roots.add((low + high) / 2);
            }
            left = right;
            leftValue = rightValue;
        }
        if (leftValue == 0) roots.add(left);
        return roots;
    }

    private static double cashFlowValue(double[] coefficients, double[] exponents, double growth) {
        double sum = 0, magnitude = 0;
        for (int i = 0; i < coefficients.length; i++) {
            double term = coefficients[i] * Math.exp(exponents[i] * growth);
            sum += term;
            magnitude += Math.abs(term);
        }
        return Math.abs(sum) <= magnitude * 1e-12 ? 0 : sum;
    }
}
