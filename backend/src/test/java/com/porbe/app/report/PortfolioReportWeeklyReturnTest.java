package com.porbe.app.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.porbe.app.importer.ImportBatch;
import com.porbe.app.market.DailyMarketBar;
import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDaily;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.portfolio.Portfolio;
import com.porbe.app.portfolio.PortfolioHistoryService;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Casos sintéticos en COP: se ejecutan los calculadores reales con repositorios simulados.
 * Los importes y tasas esperados son el resultado económico, no una copia de la fórmula actual.
 * Las tasas son decimales: 0.10 equivale a 10 %. No consulta precios ni datos personales.
 */
class PortfolioReportWeeklyReturnTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-01T18:00:00Z"), ZoneOffset.UTC);
    private final Portfolio portfolio = new Portfolio("Prueba semanal", "COP");
    private final ImportBatch batch = new ImportBatch(portfolio, "sintetico.xlsx", "0".repeat(64), 0, "test");
    private final MarketInstrument instrument = new MarketInstrument("TEST.CL");
    private final List<PortfolioOperation> operations = new ArrayList<>();
    private final List<MarketPriceDaily> prices = new ArrayList<>();
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final PortfolioOperationRepository operationRepository = mock(PortfolioOperationRepository.class);
    private final MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
    private PortfolioHistoryService history;

    @ParameterizedTest(name = "Precio final {0}: nominal {1} COP, tasa {2}")
    @CsvSource({
        "100000, 0, 0",
        "110000, 100000, 0.10",
        "90000, -100000, -0.10",
        "0, -1000000, -1"
    })
    @DisplayName("Sin aportes: precio estable, subida, caída y pérdida total")
    void valuesPriceChangesWithoutCashFlows(String finalPrice, String gain, String rate) {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        price("2026-01-02", "100000");
        price("2026-01-09", finalPrice);

        assertPerformance(report("2026-01-05", "2026-01-09"), gain, rate);
    }

    @ParameterizedTest(name = "Solo {0}: nominal 0 COP, tasa 0")
    @EnumSource(value = OperationType.class, names = {"DEPOSITO", "RETIRO"})
    @DisplayName("Un depósito o retiro de efectivo no constituye rentabilidad")
    void cashMovementsAloneDoNotGenerateReturns(OperationType type) {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", type, "200000", null);

        assertPerformance(report("2026-01-05", "2026-01-09"), "0", "0");
    }

    @Test
    @DisplayName("Dividendos de 50.000 sobre 1.000.000: ganancia 50.000 y 5 %")
    void includesDividendsOnce() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-05", OperationType.DIVIDENDO, "50000", null);
        price("2026-01-02", "100000");
        price("2026-01-09", "100000");

        assertPerformance(report("2026-01-05", "2026-01-09"), "50000", "0.05");
    }

    @Test
    @DisplayName("Comprar y vender al mismo precio sin comisiones: ganancia 0 y 0 %")
    void tradingWithoutProfitDoesNotGenerateReturns() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-08", OperationType.VENTA, "1000000", "10");
        price("2026-01-09", "100000");

        assertPerformance(report("2026-01-05", "2026-01-09"), "0", "0");
    }

    @ParameterizedTest(name = "{0} el {1}, reporte hasta {2}: nominal 0 COP, tasa 0")
    @CsvSource({
        "DEPOSITO, 2026-01-10, 2026-01-16",
        "RETIRO, 2026-01-10, 2026-01-16",
        "DEPOSITO, 2026-01-18, 2026-01-18",
        "RETIRO, 2026-01-18, 2026-01-18"
    })
    @DisplayName("Los flujos deben corresponder al intervalo entre los cierres valorados")
    void weekendCashFlowsDoNotBecomeProfitOrLoss(OperationType type, String movementDate, String to) {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation(movementDate, type, "200000", null);

        assertPerformance(report("2026-01-12", to), "0", "0");
    }

    @Test
    @DisplayName("Sin un nuevo viernes, un depósito posterior al cierre no puede generar pérdida")
    void aDepositAfterTheLastFridayDoesNotBecomeALoss() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-12", OperationType.DEPOSITO, "200000", null);

        assertThatThrownBy(() -> report("2026-01-12", "2026-01-15"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos un cierre semanal");
    }

    @ParameterizedTest(name = "Saldo {0}, {1} de {2}, inversión {3}: tasa esperada 10 %")
    @CsvSource({
        "1000000, DEPOSITO, 9000000, 10000000, 100, 1000000",
        "10000000, RETIRO, 9000000, 1000000, 10, 100000"
    })
    @DisplayName("El capital cambia antes de invertir y el precio sube 10 %: rendimiento 10 %")
    void measuresGrowthAfterAnEarlyCashFlow(
            String initialCash, OperationType type, String flow, String invested, String quantity, String gain) {
        operation("2026-01-02", OperationType.DEPOSITO, initialCash, null);
        operation("2026-01-05", type, flow, null);
        operation("2026-01-05", OperationType.COMPRA, invested, quantity);
        // Antes del flujo solo había efectivo; después se invierte todo, antes de la subida.
        price("2026-01-02", "100000");
        price("2026-01-05", "100000");
        price("2026-01-09", "110000");

        assertPerformance(report("2026-01-05", "2026-01-09"), gain, "0.10");
    }

    @Test
    @DisplayName("La ganancia de una semana anterior no debe incluirse en la semana seleccionada")
    void isolatesTheSelectedWeekFromEarlierReturns() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        price("2026-01-02", "100000");
        price("2026-01-09", "120000");
        price("2026-01-16", "132000");

        assertPerformance(report("2026-01-12", "2026-01-16"), "120000", "0.10");
    }

    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"DEPOSITO", "RETIRO"})
    @DisplayName("Un flujo posterior a la subida no altera el 10 % ya ganado")
    void preservesGrowthBeforeALateCashFlow(OperationType type) {
        var withdrawal = type == OperationType.RETIRO;
        var capital = withdrawal ? "10000000" : "1000000";
        operation("2026-01-02", OperationType.DEPOSITO, capital, null);
        operation("2026-01-02", OperationType.COMPRA, capital, withdrawal ? "100" : "10");
        if (withdrawal) {
            operation("2026-01-09", OperationType.VENTA, "9900000", "90");
        }
        operation("2026-01-09", type, withdrawal ? "9900000" : "9000000", null);
        price("2026-01-02", "100000");
        // Convención diaria: el flujo del viernes usa el cierre del viernes, tras la subida.
        price("2026-01-09", "110000");

        assertPerformance(report("2026-01-05", "2026-01-09"), withdrawal ? "1000000" : "100000", "0.10");
    }

    @Test
    @DisplayName("Depósito y retiro con flujo neto cero también requieren separar los tramos")
    void separatesOffsettingCashFlows() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-05", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-09", OperationType.VENTA, "1100000", "10");
        operation("2026-01-09", OperationType.RETIRO, "1000000", null);
        price("2026-01-02", "100000");
        price("2026-01-05", "100000");
        price("2026-01-09", "110000");

        assertPerformance(report("2026-01-05", "2026-01-09"), "200000", "0.10");
    }

    @Test
    @DisplayName("Dos subidas de 10 % separadas por un aporte se componen en 21 %")
    void compoundsReturnsAroundACashFlow() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-07", OperationType.DEPOSITO, "1100000", null);
        operation("2026-01-07", OperationType.COMPRA, "1100000", "10");
        price("2026-01-02", "100000");
        price("2026-01-07", "110000");
        price("2026-01-09", "121000");

        assertPerformance(report("2026-01-05", "2026-01-09"), "320000", "0.21");
    }

    @Test
    @DisplayName("La primera semana usa capital inicial cero y todos los aportes hasta su cierre")
    void calculatesTheFirstWeekWithoutAPriorSnapshot() {
        operation("2026-01-05", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", OperationType.COMPRA, "1000000", "10");
        price("2026-01-05", "100000");
        price("2026-01-09", "110000");

        var report = report("2026-01-07", "2026-01-09");

        assertPerformance(report, "100000", "0.10");
        assertThat(report.baselineDate()).isEqualTo(LocalDate.parse("2026-01-02"));
        assertThat(report.bestPeriodImpact().amount()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Después de perder 100 %, un nuevo aporte permite calcular el periodo siguiente")
    void measuresANewPeriodAfterTotalLossAndRecapitalization() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-12", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-14", OperationType.DIVIDENDO, "100000", null);
        price("2026-01-02", "100000");
        price("2026-01-09", "0");
        price("2026-01-16", "0");

        var report = report("2026-01-12", "2026-01-16");

        assertPerformance(report, "100000", "0.10");
        assertThat(report.timeWeightedReturn()).isEqualByComparingTo("-1");
        assertPerformance(report("2026-01-05", "2026-01-16"), "-900000", "-1");
        var lossWeek = historyService().weeklyHistory(1L, null, LocalDate.parse("2026-01-09")).weeks().getLast();
        assertThat(lossWeek.annualizedReturn()).isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("Retirar todo el efectivo no es perderlo; una semana vacía no tiene tasa")
    void distinguishesAnEmptyPortfolioFromTotalLoss() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", OperationType.RETIRO, "1000000", null);
        operation("2026-01-19", OperationType.DEPOSITO, "2000000", null);

        assertPerformance(report("2026-01-05", "2026-01-09"), "0", "0");
        var emptyWeek = historyService().weeklyHistory(1L, null, LocalDate.parse("2026-01-16")).weeks().getLast();
        assertThat(emptyWeek.periodReturn()).isNull();
        assertThat(emptyWeek.timeWeightedReturn()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> report("2026-01-12", "2026-01-16"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exista capital");
        assertPerformance(report("2026-01-19", "2026-01-23"), "0", "0");
    }

    @Test
    @DisplayName("Una posición financiada sin aportes no inventa una tasa sobre capital cero")
    void doesNotInventAReturnWithoutCapital() {
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        price("2026-01-02", "100000");
        price("2026-01-09", "110000");

        assertThatThrownBy(() -> report("2026-01-05", "2026-01-09"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exista capital");
    }

    @ParameterizedTest
    @CsvSource({"2026-01-02, 2026-01-09", "2026-01-05, 2026-01-16"})
    @DisplayName("Faltar el precio inicial o final impide publicar rendimientos ficticios")
    void rejectsMissingEndpointPrices(String purchaseDate, String firstPriceDate) {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation(purchaseDate, OperationType.COMPRA, "1000000", "10");
        price(firstPriceDate, "110000");

        assertThatThrownBy(() -> report("2026-01-05", "2026-01-09"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precios de cierre");
        var week = historyService().weeklyHistory(1L, null, LocalDate.parse("2026-01-09")).weeks().getLast();
        assertThat(week.periodReturn()).isNull();
        assertThat(week.timeWeightedReturn()).isNull();
    }

    @Test
    @DisplayName("También se necesita una valoración válida cuando ocurre un flujo intermedio")
    void rejectsAMissingPriceAtTheCashFlow() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-05", OperationType.COMPRA, "1000000", "10");
        operation("2026-01-06", OperationType.DEPOSITO, "100000", null);
        price("2026-01-09", "110000");

        assertThatThrownBy(() -> report("2026-01-05", "2026-01-09"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precios de cierre");
    }

    @ParameterizedTest
    @CsvSource({"false, true", "true, false"})
    @DisplayName("Los precios provisionales no se presentan como rendimientos cerrados")
    void rejectsProvisionalEndpointPrices(boolean initialFinal, boolean endingFinal) {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        price("2026-01-02", "100000", initialFinal);
        price("2026-01-09", "110000", endingFinal);

        assertThatThrownBy(() -> report("2026-01-05", "2026-01-09"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precios de cierre");
    }

    @Test
    @DisplayName("Un hueco antiguo no impide calcular una semana posterior con ambos cierres válidos")
    void recoversWeeklyPerformanceWithoutInventingCumulativePerformance() {
        operation("2026-01-02", OperationType.DEPOSITO, "1000000", null);
        operation("2026-01-02", OperationType.COMPRA, "1000000", "10");
        price("2026-01-09", "100000");
        price("2026-01-16", "110000");

        var report = report("2026-01-12", "2026-01-16");

        assertPerformance(report, "100000", "0.10");
        assertThat(report.timeWeightedReturn()).isNull();
        assertThat(report.valuationComplete()).isFalse();
        assertThat(new PortfolioReportTemplateModelFactory().create(report).totalPerformance()).isEqualTo("N/D");
    }

    private PortfolioReportData report(String from, String to) {
        var history = historyService();
        return new PortfolioReportCalculator(history, portfolioService, operationRepository, instrumentRepository, CLOCK)
                .calculate(1L, LocalDate.parse(from), LocalDate.parse(to));
    }

    private PortfolioHistoryService historyService() {
        if (history != null) {
            return history;
        }
        when(portfolioService.getPortfolio(1L)).thenReturn(portfolio);
        // El orden estable conserva el orden de inserción de las operaciones de un mismo día.
        var ordered = operations.stream().sorted(Comparator.comparing(PortfolioOperation::getDate)).toList();
        when(operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio)).thenReturn(ordered);
        when(operationRepository.findAllByPortfolioAndDateBetweenOrderByDateAscIdAsc(
                eq(portfolio), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(call -> {
                    LocalDate start = call.getArgument(1);
                    LocalDate end = call.getArgument(2);
                    return ordered.stream()
                            .filter(operation -> !operation.getDate().isBefore(start) && !operation.getDate().isAfter(end))
                            .toList();
                });
        when(instrumentRepository.findByTickerIn(any())).thenReturn(List.of(instrument));
        var priceRepository = mock(MarketPriceDailyRepository.class);
        when(priceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateAsc(
                eq(instrument), any(LocalDate.class)))
                .thenAnswer(call -> {
                    LocalDate cutoff = call.getArgument(1);
                    return prices.stream().filter(price -> !price.getPriceDate().isAfter(cutoff))
                            .sorted(Comparator.comparing(MarketPriceDaily::getPriceDate)).toList();
                });
        history = new PortfolioHistoryService(
                portfolioService, operationRepository, instrumentRepository, priceRepository, CLOCK);
        return history;
    }

    private void operation(String date, OperationType type, String amount, String quantity) {
        var cashMovement = type == OperationType.DEPOSITO || type == OperationType.RETIRO;
        var total = new BigDecimal(amount);
        var units = quantity == null ? null : new BigDecimal(quantity);
        operations.add(new PortfolioOperation(
                portfolio, batch, LocalDate.parse(date), type, cashMovement ? null : instrument.getTicker(),
                cashMovement ? null : "Acción de prueba", units,
                units == null ? null : total.divide(units, 8, RoundingMode.HALF_UP), BigDecimal.ZERO, total, null));
    }

    private void price(String date, String amount) {
        price(date, amount, true);
    }

    private void price(String date, String amount, boolean finalClose) {
        var close = new BigDecimal(amount);
        prices.add(new MarketPriceDaily(instrument,
                new DailyMarketBar(LocalDate.parse(date), close, close, close, close, close, 100L, finalClose),
                "TEST", OffsetDateTime.now(CLOCK)));
    }

    private void assertPerformance(PortfolioReportData report, String gain, String rate) {
        assertAll(
                () -> assertThat(report.periodGain()).as("Resultado nominal en COP").isEqualByComparingTo(gain),
                () -> assertThat(report.periodReturn()).as("Rendimiento como tasa decimal").isEqualByComparingTo(rate));
    }
}
