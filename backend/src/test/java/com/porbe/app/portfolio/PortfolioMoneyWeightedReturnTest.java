package com.porbe.app.portfolio;

import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.moneyWeightedReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.porbe.app.portfolio.PortfolioPerformanceCalculator.CashFlow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PortfolioMoneyWeightedReturnTest {
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = START.plusDays(365);

    @ParameterizedTest
    @CsvSource({"1000,0", "1100,0.10", "500,-0.50", "0,-1", "11000,10"})
    void valuesAnInvestmentWithoutIntermediateFlows(String endValue, String expected) {
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), END, amount(endValue), true))
                .isEqualByComparingTo(expected);
    }

    @Test
    void separatesPeriodReturnFromAnnualizedReturn() {
        var end = START.plusDays(730);
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), end, amount("1210"), false))
                .isEqualByComparingTo("0.21");
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), end, amount("1210"), true))
                .isEqualByComparingTo("0.10");
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), START.plusDays(100), amount("1100"), false))
                .isEqualByComparingTo("0.10");
    }

    @Test
    void respectsTheDateAndSignOfDepositsAndWithdrawals() {
        var midway = START.plusDays(365);
        var end = START.plusDays(730);
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(new CashFlow(midway, amount("-1000"))),
                end, amount("2310"), true)).isEqualByComparingTo("0.10");
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(new CashFlow(midway, amount("500"))),
                end, amount("660"), true)).isEqualByComparingTo("0.10");
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(new CashFlow(END, amount("-1000"))),
                END, amount("2100"), true)).isEqualByComparingTo("0.10");
    }

    @Test
    void matchesThePublishedExcelXirrExampleWithIrregularDates() {
        // https://support.microsoft.com/en-us/Excel/functions/xirr-function
        var flows = List.of(
                new CashFlow(LocalDate.of(2008, 1, 1), amount("-10000")),
                new CashFlow(LocalDate.of(2008, 3, 1), amount("2750")),
                new CashFlow(LocalDate.of(2008, 10, 30), amount("4250")),
                new CashFlow(LocalDate.of(2009, 2, 15), amount("3250")));
        assertThat(moneyWeightedReturn(LocalDate.of(2008, 1, 1), BigDecimal.ZERO, flows,
                LocalDate.of(2009, 4, 1), amount("2750"), true))
                .isCloseTo(amount("0.373362535"), within(amount("0.00000001")));
    }

    @Test
    void rejectsAmbiguousRatesAndAcceptsAnExactlyRepeatedRoot() {
        var end = START.plusDays(730);
        var multiple = List.of(new CashFlow(START.plusDays(365), amount("230")), new CashFlow(end, amount("-132")));
        assertThat(moneyWeightedReturn(START, amount("100"), multiple, end, BigDecimal.ZERO, true)).isNull();
        var repeated = List.of(new CashFlow(START.plusDays(365), amount("200")), new CashFlow(end, amount("-100")));
        assertThat(moneyWeightedReturn(START, amount("100"), repeated, end, BigDecimal.ZERO, true))
                .isEqualByComparingTo("0");
    }

    @Test
    void returnsUnavailableForMissingValuesOrNoInvestmentDuration() {
        assertThat(moneyWeightedReturn(START, null, List.of(), END, amount("1000"), true)).isNull();
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), END, null, true)).isNull();
        assertThat(moneyWeightedReturn(START, BigDecimal.ZERO, List.of(), END, amount("1000"), true)).isNull();
        assertThat(moneyWeightedReturn(START, BigDecimal.ZERO, List.of(), END, BigDecimal.ZERO, true)).isNull();
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), START, amount("1000"), true)).isNull();
        assertThat(moneyWeightedReturn(START, amount("1000"), List.of(), END, amount("-1000"), true)).isNull();
        assertThat(moneyWeightedReturn(START, BigDecimal.ZERO,
                List.of(new CashFlow(END, amount("-1000"))), END, BigDecimal.ZERO, true)).isNull();
    }

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
