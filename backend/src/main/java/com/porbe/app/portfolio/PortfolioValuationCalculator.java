package com.porbe.app.portfolio;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketPriceDaily;
import com.porbe.app.market.MarketSectorCatalog;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Acumula operaciones en orden cronológico y valora posiciones con los precios suministrados. */
public final class PortfolioValuationCalculator {

    private static final int CALCULATION_SCALE = 16;
    private static final int VALUE_SCALE = 8;

    private final String baseCurrency;
    private final Map<String, MarketInstrument> instruments;
    private final Map<String, PortfolioPositionLedger> ledgers = new LinkedHashMap<>();
    private BigDecimal cashBalance = BigDecimal.ZERO;
    private BigDecimal netContributions = BigDecimal.ZERO;

    PortfolioValuationCalculator(String baseCurrency, Map<String, MarketInstrument> instruments) {
        this.baseCurrency = baseCurrency;
        this.instruments = instruments;
    }

    void apply(PortfolioOperation operation) {
        var ticker = operation.getTicker() == null ? null : normalizeTicker(operation.getTicker());
        if (ticker != null) {
            ledgers.computeIfAbsent(ticker, PortfolioPositionLedger::new).apply(operation);
        }
        var cashImpact = operation.getTotalAmount().multiply(BigDecimal.valueOf(operation.getType().cashSign()));
        if (ticker == null || !isForeign(ticker)) {
            cashBalance = cashBalance.add(cashImpact);
        }
        if (isContribution(operation)) {
            netContributions = netContributions.add(cashImpact);
        }
    }

    List<PortfolioPositionResponse> positions(Function<String, MarketPriceDaily> priceAtCutoff) {
        return ledgers.values().stream()
                .map(ledger -> valuePosition(ledger, instruments.get(ledger.ticker()), priceAtCutoff.apply(ledger.ticker())))
                .toList();
    }

    private PortfolioPositionResponse valuePosition(
            PortfolioPositionLedger ledger,
            MarketInstrument instrument,
            MarketPriceDaily latest) {
        var currency = instrument == null || instrument.getCurrency() == null
                ? baseCurrency
                : instrument.getCurrency().toUpperCase(Locale.ROOT);
        var foreignCurrency = !baseCurrency.equals(currency);
        var closed = ledger.netQuantity().signum() == 0;
        var valued = ledger.calculationComplete() && (closed || latest != null);
        var marketValue = valued
                ? closed ? BigDecimal.ZERO : ledger.netQuantity().multiply(latest.getClose())
                : null;
        var unrealizedGain = valued ? marketValue.subtract(ledger.costBasis()) : null;
        var totalGain = valued
                ? ledger.realizedGain().add(unrealizedGain).add(ledger.dividends())
                : null;

        return new PortfolioPositionResponse(
                ledger.ticker(),
                instrument != null && instrument.getName() != null ? instrument.getName() : ledger.name(),
                currency,
                instrument == null
                        ? MarketSectorCatalog.suggestedSector(ledger.ticker())
                        : instrument.getSector(),
                roundValue(ledger.netQuantity()),
                ledger.calculationComplete() && !closed
                        ? roundValue(ledger.costBasis().divide(
                                ledger.netQuantity(),
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP))
                        : null,
                ledger.calculationComplete() ? roundMoney(ledger.costBasis()) : null,
                roundMoney(ledger.totalPurchases()),
                latest == null ? null : roundValue(latest.getClose()),
                latest == null ? null : latest.getPriceDate(),
                latest != null && !latest.isFinalClose(),
                marketValue == null ? null : roundMoney(marketValue),
                null,
                ledger.calculationComplete() ? roundMoney(ledger.realizedGain()) : null,
                unrealizedGain == null ? null : roundMoney(unrealizedGain),
                roundMoney(ledger.dividends()),
                totalGain == null ? null : roundMoney(totalGain),
                totalGain == null ? null : returnRate(totalGain, ledger.totalPurchases()),
                closed,
                valued,
                ledger.calculationComplete(),
                foreignCurrency);
    }

    BigDecimal cashBalance() {
        return cashBalance;
    }

    BigDecimal netContributions() {
        return netContributions;
    }

    // El historial suma los importes contables antes de redondear el consolidado.
    BigDecimal realizedGain() {
        return sumEligibleLedgers(PortfolioPositionLedger::realizedGain);
    }

    BigDecimal totalPurchases() {
        return sumEligibleLedgers(PortfolioPositionLedger::totalPurchases);
    }

    private BigDecimal sumEligibleLedgers(Function<PortfolioPositionLedger, BigDecimal> amount) {
        return ledgers.values().stream()
                .filter(ledger -> !isForeign(ledger.ticker()) && ledger.calculationComplete())
                .map(amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isForeign(String ticker) {
        var instrument = instruments.get(ticker);
        return instrument != null && instrument.getCurrency() != null
                && !baseCurrency.equals(instrument.getCurrency().toUpperCase(Locale.ROOT));
    }

    static Set<String> tickers(List<PortfolioOperation> operations) {
        return operations.stream().map(PortfolioOperation::getTicker).filter(Objects::nonNull)
                .map(PortfolioValuationCalculator::normalizeTicker)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static MarketPriceDaily latestPrice(NavigableMap<LocalDate, MarketPriceDaily> prices, LocalDate cutoff) {
        var entry = prices == null ? null : prices.floorEntry(cutoff);
        return entry == null ? null : entry.getValue();
    }

    public static BigDecimal netContributions(List<PortfolioOperation> operations) {
        return operations.stream().filter(PortfolioValuationCalculator::isContribution)
                .map(operation -> operation.getTotalAmount().multiply(BigDecimal.valueOf(operation.getType().cashSign())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isContribution(PortfolioOperation operation) {
        return operation.getType() == OperationType.DEPOSITO || operation.getType() == OperationType.RETIRO;
    }

    static <T> BigDecimal sumAmounts(List<T> items, Function<T, BigDecimal> amount) {
        return items.stream().map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal returnRate(BigDecimal gain, BigDecimal invested) {
        return invested.signum() == 0 ? BigDecimal.ZERO.setScale(VALUE_SCALE)
                : gain.divide(invested, VALUE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal roundMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundValue(BigDecimal amount) {
        return amount.setScale(VALUE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static String normalizeTicker(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
}
