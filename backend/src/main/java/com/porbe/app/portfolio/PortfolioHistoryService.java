package com.porbe.app.portfolio;

import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.annualizedReturn;
import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.compoundGrowth;
import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.growthFactor;
import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.moneyWeightedReturn;
import static com.porbe.app.portfolio.PortfolioPerformanceCalculator.returnFromGrowth;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.latestPrice;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.roundMoney;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.returnRate;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.sumAmounts;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDaily;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.operation.PortfolioOperationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioHistoryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final int VALUE_SCALE = 8;

    private final PortfolioService portfolioService;
    private final PortfolioOperationRepository operationRepository;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceDailyRepository priceRepository;
    private final Clock clock;

    public PortfolioHistoryService(
            PortfolioService portfolioService,
            PortfolioOperationRepository operationRepository,
            MarketInstrumentRepository instrumentRepository,
            MarketPriceDailyRepository priceRepository,
            Clock clock) {
        this.portfolioService = portfolioService;
        this.operationRepository = operationRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    /**
     * Aplica las operaciones una sola vez en orden cronológico y toma para cada
     * viernes el último cierre conocido, incluso cuando ese viernes fue festivo.
     * El TWR enlaza tramos separados por depósitos/retiros en orden fecha/id;
     * conserva el factor cero de una pérdida total y deja sin tasa las semanas sin capital.
     */
    @Transactional
    public PortfolioHistoryResponse weeklyHistory(Long portfolioId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        var portfolio = portfolioService.getPortfolio(portfolioId);
        var operations = operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio);
        var lastCompletedWeek = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));

        if (operations.isEmpty()) {
            return emptyHistory(portfolio.getBaseCurrency(), lastCompletedWeek);
        }

        var firstWeek = operations.getFirst().getDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

        var effectiveFrom = from == null ? firstWeek : from;
        var effectiveTo = to == null || to.isAfter(lastCompletedWeek) ? lastCompletedWeek : to;

        if (firstWeek.isAfter(effectiveTo)) {
            return emptyHistory(portfolio.getBaseCurrency(), lastCompletedWeek, operations.size());
        }

        var baseCurrency = portfolio.getBaseCurrency().toUpperCase(Locale.ROOT);
        var instruments = instrumentsByTicker(PortfolioValuationCalculator.tickers(operations));
        var prices = pricesByTicker(instruments, lastCompletedWeek);
        var calculator = new PortfolioValuationCalculator(baseCurrency, instruments);
        var weeks = new ArrayList<PortfolioWeeklySnapshot>();
        var operationIndex = 0;
        BigDecimal previousPortfolioValue = null;
        var previousNetContributions = BigDecimal.ZERO;
        var cumulativeGrowth = BigDecimal.ONE;
        BigDecimal previousPerformanceValue = BigDecimal.ZERO;
        var everFunded = false;

        for (var week = firstWeek; !week.isAfter(effectiveTo); week = week.plusWeeks(1)) {
            var segmentStart = previousPerformanceValue;
            var weeklyGrowth = BigDecimal.ONE;
            var hasCapital = segmentStart != null && segmentStart.signum() > 0;
            while (operationIndex < operations.size()
                    && !operations.get(operationIndex).getDate().isAfter(week)) {
                var operation = operations.get(operationIndex++);
                if (PortfolioValuationCalculator.isContribution(operation)) {
                    // ponytail: sin horas intradía, cada flujo usa el último cierre de su fecha.
                    // Con horas y cotizaciones intradía se puede valorar el instante exacto del flujo.
                    var beforeFlow = performanceValue(snapshot(operation.getDate(), calculator, prices, null));
                    weeklyGrowth = compoundGrowth(weeklyGrowth, growthFactor(segmentStart, beforeFlow));
                    var contributionsBefore = calculator.netContributions();
                    calculator.apply(operation);
                    var flow = calculator.netContributions().subtract(contributionsBefore);
                    segmentStart = beforeFlow == null ? null : beforeFlow.add(flow);
                    hasCapital |= segmentStart != null && segmentStart.signum() > 0;
                } else {
                    calculator.apply(operation);
                }
            }

            var snapshot = snapshot(week, calculator, prices, previousPortfolioValue);
            var netContributions = calculator.netContributions();
            var externalCashFlow = netContributions.subtract(previousNetContributions);
            previousPerformanceValue = performanceValue(snapshot);
            weeklyGrowth = compoundGrowth(weeklyGrowth, growthFactor(segmentStart, previousPerformanceValue));
            cumulativeGrowth = compoundGrowth(cumulativeGrowth, weeklyGrowth);
            everFunded |= hasCapital;
            snapshot = snapshot.withPerformance(
                    roundMoney(externalCashFlow),
                    hasCapital ? returnFromGrowth(weeklyGrowth) : null,
                    everFunded ? returnFromGrowth(cumulativeGrowth) : null,
                    everFunded ? annualizedReturn(cumulativeGrowth, operations.getFirst().getDate(), week) : null);
            previousPortfolioValue = snapshot.portfolioValue();
            previousNetContributions = netContributions;
            if (!week.isBefore(effectiveFrom)) {
                weeks.add(snapshot);
            }
        }

        BigDecimal totalMwr = null;
        BigDecimal yearMwr = null;
        BigDecimal annualizedMwr = null;
        if (!weeks.isEmpty()) {
            var end = weeks.getLast().weekEnding();
            var finalValue = performanceValue(weeks.getLast());
            var flows = operations.stream()
                    .filter(operation -> !operation.getDate().isAfter(end))
                    .filter(PortfolioValuationCalculator::isContribution)
                    .filter(operation -> operation.getTotalAmount().signum() != 0)
                    .map(operation -> new PortfolioPerformanceCalculator.CashFlow(operation.getDate(),
                            operation.getTotalAmount().multiply(BigDecimal.valueOf(-operation.getType().cashSign()))))
                    .toList();
            if (!flows.isEmpty()) {
                var firstContribution = flows.getFirst().date();
                totalMwr = moneyWeightedReturn(firstContribution, BigDecimal.ZERO, flows, end, finalValue, false);
                annualizedMwr = moneyWeightedReturn(firstContribution, BigDecimal.ZERO, flows, end, finalValue, true);
                var yearStart = LocalDate.of(end.getYear(), 1, 1);
                var opening = new PortfolioValuationCalculator(baseCurrency, instruments);
                operations.stream().filter(operation -> operation.getDate().isBefore(yearStart)).forEach(opening::apply);
                var initialValue = performanceValue(snapshot(yearStart.minusDays(1), opening, prices, null));
                var yearFlows = flows.stream().filter(flow -> !flow.date().isBefore(yearStart)).toList();
                var start = firstContribution.isBefore(yearStart) ? yearStart.minusDays(1) : firstContribution;
                if (initialValue != null && initialValue.signum() == 0 && !yearFlows.isEmpty()) {
                    start = yearFlows.getFirst().date();
                }
                yearMwr = moneyWeightedReturn(start, initialValue, yearFlows, end, finalValue, false);
            }
        }
        return new PortfolioHistoryResponse(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                baseCurrency,
                weeks.isEmpty() ? null : weeks.getFirst().weekEnding(),
                weeks.isEmpty() ? null : weeks.getLast().weekEnding(),
                lastCompletedWeek,
                operations.size(),
                weeks.size(),
                weeks.stream().allMatch(PortfolioWeeklySnapshot::valuationComplete),
                totalMwr,
                yearMwr,
                annualizedMwr,
                weeks);
    }

    /** Construye el consolidado y el detalle por ticker para un único cierre semanal. */
    private PortfolioWeeklySnapshot snapshot(
            LocalDate week,
            PortfolioValuationCalculator calculator,
            Map<String, NavigableMap<LocalDate, MarketPriceDaily>> prices,
            BigDecimal previousPortfolioValue) {
        var positions = calculator.positions(ticker -> latestPrice(prices.get(ticker), week)).stream()
                .map(PortfolioWeeklyPositionResponse::from)
                .sorted(Comparator.comparing(PortfolioWeeklyPositionResponse::ticker))
                .toList();
        var eligible = positions.stream()
                .filter(position -> !position.foreignCurrency() && position.calculationComplete())
                .toList();
        var marketValue = sumAmounts(eligible, PortfolioWeeklyPositionResponse::marketValue);
        var investedCapital = sumAmounts(eligible, PortfolioWeeklyPositionResponse::costBasis);
        var dividends = sumAmounts(eligible, PortfolioWeeklyPositionResponse::dividends);
        var realizedGain = calculator.realizedGain();
        var totalPurchases = calculator.totalPurchases();
        var unrealizedGain = eligible.stream()
                .filter(PortfolioWeeklyPositionResponse::valued)
                .map(position -> position.marketValue().subtract(position.costBasis()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalGain = realizedGain.add(unrealizedGain).add(dividends);
        var cashBalance = calculator.cashBalance();
        var portfolioValue = marketValue.add(cashBalance);
        var nominalVariation = previousPortfolioValue == null
                ? null
                : portfolioValue.subtract(previousPortfolioValue);
        var percentageVariation = previousPortfolioValue == null || previousPortfolioValue.signum() == 0
                ? null
                : nominalVariation.divide(previousPortfolioValue, VALUE_SCALE, RoundingMode.HALF_UP);
        var unpriced = (int) positions.stream()
                .filter(position -> !position.foreignCurrency()
                        && position.quantity().signum() != 0
                        && !position.valued())
                .count();
        var foreign = (int) positions.stream().filter(PortfolioWeeklyPositionResponse::foreignCurrency).count();
        var inconsistent = (int) positions.stream()
                .filter(position -> !position.calculationComplete())
                .count();

        return new PortfolioWeeklySnapshot(
                week,
                roundMoney(marketValue),
                roundMoney(investedCapital),
                roundMoney(calculator.netContributions()),
                roundMoney(dividends),
                roundMoney(cashBalance),
                roundMoney(portfolioValue),
                roundMoney(realizedGain),
                roundMoney(unrealizedGain),
                roundMoney(totalGain),
                returnRate(totalGain, totalPurchases),
                null,
                null,
                null,
                null,
                nominalVariation == null ? null : roundMoney(nominalVariation),
                percentageVariation,
                unpriced == 0 && foreign == 0 && inconsistent == 0,
                unpriced,
                foreign,
                inconsistent,
                positions);
    }

    /** No confunde una valoración parcial o provisional con una pérdida de capital. */
    private BigDecimal performanceValue(PortfolioWeeklySnapshot snapshot) {
        return snapshot.valuationComplete() && snapshot.positions().stream()
                .noneMatch(position -> position.quantity().signum() != 0 && position.provisionalPrice())
                ? snapshot.portfolioValue() : null;
    }

    private Map<String, MarketInstrument> instrumentsByTicker(java.util.Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        return instrumentRepository.findByTickerIn(tickers).stream()
                .collect(Collectors.toMap(MarketInstrument::getTicker, Function.identity()));
    }

    private Map<String, NavigableMap<LocalDate, MarketPriceDaily>> pricesByTicker(
            Map<String, MarketInstrument> instruments,
            LocalDate lastCompletedWeek) {
        var result = new LinkedHashMap<String, NavigableMap<LocalDate, MarketPriceDaily>>();
        instruments.forEach((ticker, instrument) -> {
            var history = new TreeMap<LocalDate, MarketPriceDaily>();
            priceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateAsc(instrument, lastCompletedWeek)
                    .forEach(price -> history.put(price.getPriceDate(), price));
            result.put(ticker, history);
        });
        return result;
    }

    private PortfolioHistoryResponse emptyHistory(String baseCurrency, LocalDate lastCompletedWeek) {
        return emptyHistory(baseCurrency, lastCompletedWeek, 0);
    }

    private PortfolioHistoryResponse emptyHistory(
            String baseCurrency,
            LocalDate lastCompletedWeek,
            long operationCount) {
        return new PortfolioHistoryResponse(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                baseCurrency.toUpperCase(Locale.ROOT),
                null,
                null,
                lastCompletedWeek,
                operationCount,
                0,
                true,
                null,
                null,
                null,
                List.of());
    }
}
