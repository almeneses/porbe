package com.porbe.app.portfolio;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDailyRepository;
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

@Service
/** Calcula posiciones y valoración actual a partir del libro completo de operaciones. */
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
    public PortfolioSummaryResponse currentSummary() {
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var operations = operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio);
        var baseCurrency = portfolio.getBaseCurrency().toUpperCase(Locale.ROOT);
        var builders = positionBuilders(operations);
        var instruments = instrumentsByTicker(builders.keySet());
        var issues = new ArrayList<PortfolioValuationIssue>();
        var positions = builders.values().stream()
                .map(builder -> position(builder, instruments.get(builder.ticker), baseCurrency, issues))
                .sorted(positionComparator())
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

    private Map<String, PositionBuilder> positionBuilders(List<PortfolioOperation> operations) {
        var builders = new LinkedHashMap<String, PositionBuilder>();
        operations.stream()
                .filter(operation -> operation.getTicker() != null)
                .forEach(operation -> builders
                        .computeIfAbsent(normalizeTicker(operation.getTicker()), PositionBuilder::new)
                        .apply(operation));
        return builders;
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
            PositionBuilder builder,
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
        var closed = builder.netQuantity.signum() == 0;
        var valued = builder.calculationComplete && (closed || latest != null);
        var marketValue = valued
                ? closed ? BigDecimal.ZERO : builder.netQuantity.multiply(latest.getClose())
                : null;
        var unrealizedGain = valued ? marketValue.subtract(builder.costBasis) : null;
        var totalGain = valued
                ? builder.realizedGain.add(unrealizedGain).add(builder.dividends)
                : null;

        if (!builder.calculationComplete) {
            issues.add(new PortfolioValuationIssue(
                    builder.ticker,
                    "VENTA_SIN_POSICION",
                    "La cantidad vendida supera la posición disponible. Revisa el orden y las cantidades importadas."));
        }

        return new PortfolioPositionResponse(
                builder.ticker,
                instrument != null && instrument.getName() != null ? instrument.getName() : builder.name,
                currency,
                quantity(builder.netQuantity),
                builder.calculationComplete && !closed
                        ? price(builder.costBasis.divide(builder.netQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP))
                        : null,
                builder.calculationComplete ? money(builder.costBasis) : null,
                money(builder.totalPurchases),
                latest == null ? null : price(latest.getClose()),
                latest == null ? null : latest.getPriceDate(),
                latest != null && !latest.isFinalClose(),
                marketValue == null ? null : money(marketValue),
                builder.calculationComplete ? money(builder.realizedGain) : null,
                unrealizedGain == null ? null : money(unrealizedGain),
                money(builder.dividends),
                totalGain == null ? null : money(totalGain),
                totalGain == null ? null : rate(totalGain, builder.totalPurchases),
                closed,
                valued,
                builder.calculationComplete,
                foreignCurrency);
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

    /** Estado mutable y privado usado mientras se recorre el libro cronológico. */
    private static final class PositionBuilder {

        private final String ticker;
        private String name;
        private BigDecimal netQuantity = BigDecimal.ZERO;
        private BigDecimal accountingQuantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;
        private BigDecimal totalPurchases = BigDecimal.ZERO;
        private BigDecimal realizedGain = BigDecimal.ZERO;
        private BigDecimal dividends = BigDecimal.ZERO;
        private boolean calculationComplete = true;

        private PositionBuilder(String ticker) {
            this.ticker = ticker;
        }

        /** Aplica una operación y libera costo promedio cuando se registra una venta. */
        private void apply(PortfolioOperation operation) {
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
    }
}
