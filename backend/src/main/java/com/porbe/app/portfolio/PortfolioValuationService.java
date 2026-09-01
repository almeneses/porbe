package com.porbe.app.portfolio;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.market.MarketSectorCatalog;
import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calcula posiciones y valoración actual a partir del libro completo de operaciones. */
@Service
public class PortfolioValuationService {

    private static final int CALCULATION_SCALE = 16;
    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 8;
    private final PortfolioService portfolioService;
    private final PortfolioOperationRepository operationRepository;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceDailyRepository priceRepository;
    private final Clock clock;

    public PortfolioValuationService(
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

    @Transactional
    /**
     * Reconstruye el estado actual en orden cronológico y usa costo promedio
     * ponderado para separar resultado realizado y no realizado.
     */
    public PortfolioSummaryResponse currentSummary(Long portfolioId) {
        var portfolio = portfolioService.getPortfolio(portfolioId);
        var operations = operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio);
        var baseCurrency = portfolio.getBaseCurrency().toUpperCase(Locale.ROOT);
        var ledgers = positionLedgers(operations);
        var instruments = instrumentsByTicker(ledgers.keySet());
        var issues = new ArrayList<PortfolioValuationIssue>();
        var rawPositions = ledgers.values().stream()
                .map(ledger -> position(ledger, instruments.get(ledger.ticker()), baseCurrency, issues))
                .sorted(positionComparator())
                .toList();

        var eligibleMarketValue = rawPositions.stream()
                .filter(position -> !position.foreignCurrency()
                        && position.calculationComplete()
                        && position.marketValue() != null)
                .map(PortfolioPositionResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var positions = rawPositions.stream()
                .map(position -> position.withAllocationRate(allocationRate(position, eligibleMarketValue)))
                .toList();
        var eligiblePositions = positions.stream()
                .filter(position -> !position.foreignCurrency() && position.calculationComplete())
                .toList();
        var marketValue = sumNullable(eligiblePositions, PortfolioPositionResponse::marketValue);
        var costBasis = sumNullable(eligiblePositions, PortfolioPositionResponse::costBasis);
        var dividends = sumNullable(eligiblePositions, PortfolioPositionResponse::dividends);
        var realizedGain = sumNullable(eligiblePositions, PortfolioPositionResponse::realizedGain);
        var unrealizedGain = sumNullable(eligiblePositions, PortfolioPositionResponse::unrealizedGain);
        var totalPurchases = sumNullable(eligiblePositions, PortfolioPositionResponse::totalPurchases);
        var totalGain = realizedGain.add(unrealizedGain).add(dividends);
        var cashBalance = cashBalance(operations, positions);
        var netContributions = netContributions(operations);
        var portfolioValue = marketValue.add(cashBalance);
        var unpriced = (int) positions.stream()
                .filter(position -> !position.closed() && !position.foreignCurrency() && !position.valued())
                .count();
        var foreign = (int) positions.stream().filter(PortfolioPositionResponse::foreignCurrency).count();
        var open = (int) positions.stream().filter(position -> !position.closed()).count();
        var valuationDate = positions.stream()
                .map(PortfolioPositionResponse::priceDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new PortfolioSummaryResponse(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                valuationDate,
                baseCurrency,
                operations.size(),
                open,
                money(marketValue),
                money(costBasis),
                money(cashBalance),
                money(portfolioValue),
                money(netContributions),
                money(dividends),
                money(realizedGain),
                money(unrealizedGain),
                money(totalGain),
                rate(totalGain, totalPurchases),
                issues.isEmpty() && unpriced == 0 && foreign == 0,
                unpriced,
                foreign,
                positions,
                issues);
    }

    public PortfolioSummaryResponse currentSummary() {
        return currentSummary(null);
    }

    private Map<String, PortfolioPositionLedger> positionLedgers(List<PortfolioOperation> operations) {
        var ledgers = new LinkedHashMap<String, PortfolioPositionLedger>();
        operations.stream()
                .filter(operation -> operation.getTicker() != null)
                .forEach(operation -> ledgers
                        .computeIfAbsent(normalizeTicker(operation.getTicker()), PortfolioPositionLedger::new)
                        .apply(operation));
        return ledgers;
    }

    private Map<String, MarketInstrument> instrumentsByTicker(java.util.Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        return instrumentRepository.findByTickerIn(tickers).stream()
                .collect(Collectors.toMap(MarketInstrument::getTicker, Function.identity()));
    }

    /** Une el cálculo contable con el último cierre conocido del instrumento. */
    private PortfolioPositionResponse position(
            PortfolioPositionLedger ledger,
            MarketInstrument instrument,
            String baseCurrency,
            List<PortfolioValuationIssue> issues) {
        var latest = instrument == null
                ? null
                : priceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument).orElse(null);
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

        if (!ledger.calculationComplete()) {
            issues.add(new PortfolioValuationIssue(
                    ledger.ticker(),
                    "VENTA_SIN_POSICION",
                    "La cantidad vendida supera la posición disponible. Revisa el orden y las cantidades importadas."));
        }

        return new PortfolioPositionResponse(
                ledger.ticker(),
                instrument != null && instrument.getName() != null ? instrument.getName() : ledger.name(),
                currency,
                instrument == null
                        ? MarketSectorCatalog.suggestedSector(ledger.ticker())
                        : instrument.getSector(),
                quantity(ledger.netQuantity()),
                ledger.calculationComplete() && !closed
                        ? price(ledger.costBasis().divide(
                                ledger.netQuantity(),
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP))
                        : null,
                ledger.calculationComplete() ? money(ledger.costBasis()) : null,
                money(ledger.totalPurchases()),
                latest == null ? null : price(latest.getClose()),
                latest == null ? null : latest.getPriceDate(),
                latest != null && !latest.isFinalClose(),
                marketValue == null ? null : money(marketValue),
                null,
                ledger.calculationComplete() ? money(ledger.realizedGain()) : null,
                unrealizedGain == null ? null : money(unrealizedGain),
                money(ledger.dividends()),
                totalGain == null ? null : money(totalGain),
                totalGain == null ? null : rate(totalGain, ledger.totalPurchases()),
                closed,
                valued,
                ledger.calculationComplete(),
                foreignCurrency);
    }

    private BigDecimal allocationRate(PortfolioPositionResponse position, BigDecimal totalMarketValue) {
        if (position.foreignCurrency()
                || position.marketValue() == null
                || totalMarketValue.signum() == 0) {
            return null;
        }
        return position.marketValue().divide(totalMarketValue, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal cashBalance(
            List<PortfolioOperation> operations,
            List<PortfolioPositionResponse> positions) {
        var foreignTickers = positions.stream()
                .filter(PortfolioPositionResponse::foreignCurrency)
                .map(PortfolioPositionResponse::ticker)
                .collect(Collectors.toSet());
        return operations.stream()
                .filter(operation -> operation.getTicker() == null
                        || !foreignTickers.contains(normalizeTicker(operation.getTicker())))
                .map(operation -> operation.getTotalAmount()
                        .multiply(BigDecimal.valueOf(operation.getType().cashSign())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netContributions(List<PortfolioOperation> operations) {
        return operations.stream()
                .filter(operation -> operation.getType() == OperationType.DEPOSITO
                        || operation.getType() == OperationType.RETIRO)
                .map(operation -> operation.getTotalAmount()
                        .multiply(BigDecimal.valueOf(operation.getType().cashSign())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumNullable(
            List<PortfolioPositionResponse> positions,
            Function<PortfolioPositionResponse, BigDecimal> extractor) {
        return positions.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Comparator<PortfolioPositionResponse> positionComparator() {
        return Comparator.comparing(PortfolioPositionResponse::closed)
                .thenComparing(
                        PortfolioPositionResponse::marketValue,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PortfolioPositionResponse::ticker);
    }

    private BigDecimal rate(BigDecimal gain, BigDecimal invested) {
        if (invested.signum() == 0) {
            return BigDecimal.ZERO.setScale(PRICE_SCALE);
        }
        return gain.divide(invested, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal quantity(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String normalizeTicker(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

}
