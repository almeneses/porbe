package com.porbe.app.portfolio;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDaily;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.market.MarketSectorCatalog;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconstruye el portafolio al cierre de cada semana usando precios diarios persistidos. */
@Service
public class PortfolioHistoryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final int MONEY_SCALE = 2;
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
     */
    @Transactional
    public PortfolioHistoryResponse weeklyHistory(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
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
        var tickers = operations.stream()
                .map(PortfolioOperation::getTicker)
                .filter(Objects::nonNull)
                .map(this::normalizeTicker)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        var instruments = instrumentsByTicker(tickers);
        var prices = pricesByTicker(instruments, lastCompletedWeek);
        var foreignTickers = instruments.values().stream()
                .filter(instrument -> instrument.getCurrency() != null
                        && !baseCurrency.equals(instrument.getCurrency().toUpperCase(Locale.ROOT)))
                .map(MarketInstrument::getTicker)
                .collect(Collectors.toSet());

        var ledgers = new LinkedHashMap<String, PortfolioPositionLedger>();
        var weeks = new ArrayList<PortfolioWeeklySnapshot>();
        var operationIndex = 0;
        var cashBalance = BigDecimal.ZERO;
        var netContributions = BigDecimal.ZERO;
        BigDecimal previousPortfolioValue = null;
        var previousNetContributions = BigDecimal.ZERO;
        var cumulativeGrowth = BigDecimal.ONE;

        for (var week = firstWeek; !week.isAfter(effectiveTo); week = week.plusWeeks(1)) {
            while (operationIndex < operations.size()
                    && !operations.get(operationIndex).getDate().isAfter(week)) {
                var operation = operations.get(operationIndex++);
                if (operation.getTicker() != null) {
                    var ticker = normalizeTicker(operation.getTicker());
                    ledgers.computeIfAbsent(ticker, PortfolioPositionLedger::new).apply(operation);
                }
                if (operation.getTicker() == null
                        || !foreignTickers.contains(normalizeTicker(operation.getTicker()))) {
                    cashBalance = cashBalance.add(cashImpact(operation));
                }
                if (operation.getType() == OperationType.DEPOSITO
                        || operation.getType() == OperationType.RETIRO) {
                    netContributions = netContributions.add(cashImpact(operation));
                }
            }

            var snapshot = snapshot(
                    week,
                    ledgers,
                    instruments,
                    prices,
                    baseCurrency,
                    cashBalance,
                    netContributions,
                    previousPortfolioValue);
            var externalCashFlow = netContributions.subtract(previousNetContributions);
            var periodReturn = periodReturn(
                    snapshot.portfolioValue(),
                    previousPortfolioValue,
                    externalCashFlow);
            if (periodReturn != null && BigDecimal.ONE.add(periodReturn).signum() > 0) {
                cumulativeGrowth = cumulativeGrowth.multiply(BigDecimal.ONE.add(periodReturn));
            }
            var timeWeightedReturn = cumulativeGrowth.subtract(BigDecimal.ONE)
                    .setScale(VALUE_SCALE, RoundingMode.HALF_UP);
            snapshot = snapshot.withPerformance(
                    money(externalCashFlow),
                    periodReturn,
                    timeWeightedReturn,
                    annualizedReturn(cumulativeGrowth, firstWeek, week));
            previousPortfolioValue = snapshot.portfolioValue();
            previousNetContributions = netContributions;
            if (!week.isBefore(effectiveFrom)) {
                weeks.add(snapshot);
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
                weeks);
    }

    /** Construye el consolidado y el detalle por ticker para un único cierre semanal. */
    private PortfolioWeeklySnapshot snapshot(
            LocalDate week,
            Map<String, PortfolioPositionLedger> ledgers,
            Map<String, MarketInstrument> instruments,
            Map<String, NavigableMap<LocalDate, MarketPriceDaily>> prices,
            String baseCurrency,
            BigDecimal cashBalance,
            BigDecimal netContributions,
            BigDecimal previousPortfolioValue) {
        var positions = ledgers.values().stream()
                .map(ledger -> weeklyPosition(
                        ledger,
                        instruments.get(ledger.ticker()),
                        latestPrice(prices.get(ledger.ticker()), week),
                        baseCurrency))
                .sorted(Comparator.comparing(PortfolioWeeklyPositionResponse::ticker))
                .toList();
        var eligible = positions.stream()
                .filter(position -> !position.foreignCurrency() && position.calculationComplete())
                .toList();
        var marketValue = sumNullable(eligible, PortfolioWeeklyPositionResponse::marketValue);
        var investedCapital = sumNullable(eligible, PortfolioWeeklyPositionResponse::costBasis);
        var dividends = sumNullable(eligible, PortfolioWeeklyPositionResponse::dividends);
        var realizedGain = ledgers.values().stream()
                .filter(ledger -> !isForeign(ledger.ticker(), instruments, baseCurrency)
                        && ledger.calculationComplete())
                .map(PortfolioPositionLedger::realizedGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPurchases = ledgers.values().stream()
                .filter(ledger -> !isForeign(ledger.ticker(), instruments, baseCurrency)
                        && ledger.calculationComplete())
                .map(PortfolioPositionLedger::totalPurchases)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var unrealizedGain = eligible.stream()
                .filter(PortfolioWeeklyPositionResponse::valued)
                .map(position -> position.marketValue().subtract(position.costBasis()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalGain = realizedGain.add(unrealizedGain).add(dividends);
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
                money(marketValue),
                money(investedCapital),
                money(netContributions),
                money(dividends),
                money(cashBalance),
                money(portfolioValue),
                money(realizedGain),
                money(unrealizedGain),
                money(totalGain),
                rate(totalGain, totalPurchases),
                null,
                null,
                null,
                null,
                nominalVariation == null ? null : money(nominalVariation),
                percentageVariation,
                unpriced == 0 && foreign == 0 && inconsistent == 0,
                unpriced,
                foreign,
                inconsistent,
                positions);
    }

    private PortfolioWeeklyPositionResponse weeklyPosition(
            PortfolioPositionLedger ledger,
            MarketInstrument instrument,
            MarketPriceDaily price,
            String baseCurrency) {
        var currency = instrument == null || instrument.getCurrency() == null
                ? baseCurrency
                : instrument.getCurrency().toUpperCase(Locale.ROOT);
        var foreignCurrency = !baseCurrency.equals(currency);
        var closed = ledger.netQuantity().signum() == 0;
        var valued = ledger.calculationComplete() && (closed || price != null);
        var marketValue = valued
                ? closed ? BigDecimal.ZERO : ledger.netQuantity().multiply(price.getClose())
                : null;
        var totalGain = valued
                ? ledger.realizedGain().add(marketValue.subtract(ledger.costBasis())).add(ledger.dividends())
                : null;
        return new PortfolioWeeklyPositionResponse(
                ledger.ticker(),
                instrument != null && instrument.getName() != null ? instrument.getName() : ledger.name(),
                currency,
                instrument == null
                        ? MarketSectorCatalog.suggestedSector(ledger.ticker())
                        : instrument.getSector(),
                quantity(ledger.netQuantity()),
                price == null ? null : value(price.getClose()),
                price == null ? null : price.getPriceDate(),
                price != null && !price.isFinalClose(),
                marketValue == null ? null : money(marketValue),
                ledger.calculationComplete() ? money(ledger.costBasis()) : null,
                money(ledger.dividends()),
                totalGain == null ? null : money(totalGain),
                valued,
                ledger.calculationComplete(),
                foreignCurrency);
    }

    /** TWR semanal: descuenta depósitos y retiros del valor final del período. */
    private BigDecimal periodReturn(
            BigDecimal portfolioValue,
            BigDecimal previousPortfolioValue,
            BigDecimal externalCashFlow) {
        if (previousPortfolioValue == null) {
            return externalCashFlow.signum() <= 0
                    ? null
                    : portfolioValue.subtract(externalCashFlow)
                            .divide(externalCashFlow, VALUE_SCALE, RoundingMode.HALF_UP);
        }
        if (previousPortfolioValue.signum() == 0) {
            return null;
        }
        return portfolioValue.subtract(externalCashFlow)
                .divide(previousPortfolioValue, VALUE_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
    }

    private BigDecimal annualizedReturn(BigDecimal cumulativeGrowth, LocalDate firstWeek, LocalDate week) {
        var days = ChronoUnit.DAYS.between(firstWeek, week);
        if (days <= 0 || cumulativeGrowth.signum() <= 0) {
            return null;
        }
        var annualized = Math.pow(cumulativeGrowth.doubleValue(), 365.0 / days) - 1.0;
        return BigDecimal.valueOf(annualized).setScale(VALUE_SCALE, RoundingMode.HALF_UP);
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

    private MarketPriceDaily latestPrice(
            NavigableMap<LocalDate, MarketPriceDaily> prices,
            LocalDate week) {
        if (prices == null) {
            return null;
        }
        var entry = prices.floorEntry(week);
        return entry == null ? null : entry.getValue();
    }

    private boolean isForeign(
            String ticker,
            Map<String, MarketInstrument> instruments,
            String baseCurrency) {
        var instrument = instruments.get(ticker);
        return instrument != null
                && instrument.getCurrency() != null
                && !baseCurrency.equals(instrument.getCurrency().toUpperCase(Locale.ROOT));
    }

    private BigDecimal cashImpact(PortfolioOperation operation) {
        return operation.getTotalAmount().multiply(BigDecimal.valueOf(operation.getType().cashSign()));
    }

    private BigDecimal sumNullable(
            List<PortfolioWeeklyPositionResponse> positions,
            Function<PortfolioWeeklyPositionResponse, BigDecimal> extractor) {
        return positions.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal rate(BigDecimal gain, BigDecimal invested) {
        return invested.signum() == 0
                ? BigDecimal.ZERO.setScale(VALUE_SCALE)
                : gain.divide(invested, VALUE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal amount) {
        return amount.setScale(VALUE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal quantity(BigDecimal amount) {
        return value(amount);
    }

    private String normalizeTicker(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
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
                List.of());
    }
}
