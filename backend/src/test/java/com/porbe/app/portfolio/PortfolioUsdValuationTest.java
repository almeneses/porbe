package com.porbe.app.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import com.porbe.app.market.*;
import com.porbe.app.operation.*;
import com.porbe.app.importer.ImportBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class PortfolioUsdValuationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 2);

    @Test
    void reconcilesExchangesStocksFeesAndDividendsWithoutCountingMoneyTwice() {
        var calculator = new PortfolioValuationCalculator("COP", Map.of(), date -> price("COP=X", "4000", date));
        calculator.apply(operation(OperationType.DEPOSITO, null, null, "4010000", "COP"));
        calculator.apply(operation(OperationType.COMPRA_USD, null, "1000", "4010000", "COP"));
        calculator.apply(operation(OperationType.COMPRA, "HIMS", "2", "400", "USD"));
        var positions = positions(calculator);
        assertThat(calculator.cashBalance()).isEqualByComparingTo("0");
        assertThat(calculator.netContributions()).isEqualByComparingTo("4010000");
        assertThat(position(positions, "COP=X").quantity()).isEqualByComparingTo("600");
        assertThat(position(positions, "COP=X").costBasis()).isEqualByComparingTo("2406000");
        assertThat(position(positions, "HIMS").costBasis()).isEqualByComparingTo("1600000");
        assertThat(position(positions, "HIMS").currency()).isEqualTo("COP");
        assertThat(value(calculator, positions)).isEqualByComparingTo("4368000");
        assertReconciled(calculator, positions);
        assertThat(calculator.totalPurchases()).isEqualByComparingTo("4010000");
        calculator.apply(operation(OperationType.VENTA, "HIMS", "2", "440", "USD"));
        calculator.apply(operation(OperationType.DIVIDENDO, "HIMS", null, "10", "USD"));
        positions = positions(calculator);
        assertThat(position(positions, "COP=X").quantity()).isEqualByComparingTo("1050");
        assertThat(position(positions, "HIMS").quantity()).isEqualByComparingTo("0");
        assertReconciled(calculator, positions);
        calculator.apply(operation(OperationType.VENTA_USD, null, "1050", "4495000", "COP"));
        positions = positions(calculator);
        assertThat(calculator.cashBalance()).isEqualByComparingTo("4495000");
        calculator.apply(operation(OperationType.RETIRO, null, null, "4495000", "COP"));
        assertThat(calculator.cashBalance()).isEqualByComparingTo("0");
        assertThat(calculator.totalPurchases()).isEqualByComparingTo("4010000");
        assertThat(position(positions, "COP=X").quantity()).isEqualByComparingTo("0");
        assertReconciled(calculator, positions);
    }

    @Test
    void neverUsesFutureExchangeRatesAndDoesNotAssumeUnknownUsdStocksAreCop() {
        var fx = new TreeMap<LocalDate, MarketPriceDaily>();
        fx.put(DATE.plusDays(1), price("COP=X", "4000", DATE.plusDays(1)));
        var calculator = new PortfolioValuationCalculator("COP", Map.of(), date -> PortfolioValuationCalculator.latestPrice(fx, date));
        calculator.apply(operation(OperationType.COMPRA_USD, null, "100", "400000", "COP"));
        calculator.apply(operation(OperationType.COMPRA, "HIMS", "1", "100", "USD"));
        var positions = positions(calculator);
        assertThat(position(positions, "HIMS").calculationComplete()).isFalse();
        assertThat(position(positions, "HIMS").costBasis()).isNull();
        assertThat(position(positions, "COP=X").quantity()).isEqualByComparingTo("0");
        assertThat(position(positions, "COP=X").calculationComplete()).isFalse();
    }

    @Test
    void requiresCurrentFxAfterSpendingAllUsdOnStocks() {
        var calculator = new PortfolioValuationCalculator("COP", Map.of(), date -> price("COP=X", "4000", date));
        calculator.apply(operation(OperationType.COMPRA_USD, null, "100", "400000", "COP"));
        calculator.apply(operation(OperationType.COMPRA, "HIMS", "1", "100", "USD"));
        var positions = calculator.positions(ticker -> ticker.equals("COP=X") ? null : price(ticker, "120", DATE));
        assertThat(position(positions, "HIMS").valued()).isFalse();
        assertThat(position(positions, "HIMS").marketValue()).isNull();
        assertThat(position(positions, "COP=X").valued()).isTrue();
    }

    @Test
    void marksOverspendingAndMixedCurrenciesIncomplete() {
        var calculator = new PortfolioValuationCalculator("COP", Map.of(), date -> price("COP=X", "4000", date));
        calculator.apply(operation(OperationType.COMPRA_USD, null, "100", "400000", "COP"));
        calculator.apply(operation(OperationType.COMPRA, "HIMS", "2", "200", "USD"));
        calculator.apply(operation(OperationType.COMPRA, "HIMS", "1", "100", "COP"));
        var positions = positions(calculator);
        assertThat(position(positions, "COP=X").quantity()).isEqualByComparingTo("-100");
        assertThat(position(positions, "COP=X").calculationComplete()).isFalse();
        assertThat(position(positions, "HIMS").calculationComplete()).isFalse();
    }

    private PortfolioOperation operation(OperationType type, String ticker, String quantity, String total, String currency) {
        var operation = new PortfolioOperation(null, new ImportBatch(null, "test", "test", 1, "test"), DATE, type,
                ticker, ticker, quantity == null ? null : new BigDecimal(quantity), BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal(total), null);
        operation.setCurrency(currency);
        return operation;
    }
    private MarketPriceDaily price(String ticker, String close, LocalDate date) {
        var amount = new BigDecimal(close);
        return new MarketPriceDaily(new MarketInstrument(ticker), new DailyMarketBar(date, amount, amount, amount, amount, amount, 0L, true), "TEST", OffsetDateTime.now());
    }
    private List<PortfolioPositionResponse> positions(PortfolioValuationCalculator calculator) {
        return calculator.positions(ticker -> price(ticker, ticker.equals("COP=X") ? "4200" : "220", DATE));
    }
    private PortfolioPositionResponse position(List<PortfolioPositionResponse> positions, String ticker) {
        return positions.stream().filter(position -> position.ticker().equals(ticker)).findFirst().orElseThrow();
    }
    private BigDecimal value(PortfolioValuationCalculator calculator, List<PortfolioPositionResponse> positions) {
        return positions.stream().map(PortfolioPositionResponse::marketValue).reduce(calculator.cashBalance(), BigDecimal::add);
    }
    private void assertReconciled(PortfolioValuationCalculator calculator, List<PortfolioPositionResponse> positions) {
        var gain = positions.stream().map(PortfolioPositionResponse::totalGain).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(gain).isEqualByComparingTo(value(calculator, positions).subtract(calculator.netContributions()));
    }
}
