package com.porbe.app.portfolio;

import static com.porbe.app.portfolio.PortfolioValuationCalculator.roundMoney;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.returnRate;
import static com.porbe.app.portfolio.PortfolioValuationCalculator.sumAmounts;

import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.operation.PortfolioOperationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioValuationService {

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
        var instruments = instrumentsByTicker(PortfolioValuationCalculator.tickers(operations));
        var calculator = new PortfolioValuationCalculator(baseCurrency, instruments);
        operations.forEach(calculator::apply);
        var rawPositions = calculator.positions(ticker -> {
            var instrument = instruments.get(ticker);
            return instrument == null ? null
                    : priceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument).orElse(null);
        });
        var issues = rawPositions.stream()
                .filter(position -> !position.calculationComplete())
                .map(position -> new PortfolioValuationIssue(
                        position.ticker(),
                        "VENTA_SIN_POSICION",
                        "La cantidad vendida supera la posición disponible. Revisa el orden y las cantidades importadas."))
                .toList();

        var eligibleMarketValue = rawPositions.stream()
                .filter(position -> !position.foreignCurrency()
                        && position.calculationComplete()
                        && position.marketValue() != null)
                .map(PortfolioPositionResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var positions = rawPositions.stream()
                .sorted(positionComparator())
                .map(position -> position.withAllocationRate(allocationRate(position, eligibleMarketValue)))
                .toList();
        var eligiblePositions = positions.stream()
                .filter(position -> !position.foreignCurrency() && position.calculationComplete())
                .toList();
        var marketValue = sumAmounts(eligiblePositions, PortfolioPositionResponse::marketValue);
        var costBasis = sumAmounts(eligiblePositions, PortfolioPositionResponse::costBasis);
        var dividends = sumAmounts(eligiblePositions, PortfolioPositionResponse::dividends);
        var realizedGain = sumAmounts(eligiblePositions, PortfolioPositionResponse::realizedGain);
        var unrealizedGain = sumAmounts(eligiblePositions, PortfolioPositionResponse::unrealizedGain);
        var totalPurchases = sumAmounts(eligiblePositions, PortfolioPositionResponse::totalPurchases);
        var totalGain = realizedGain.add(unrealizedGain).add(dividends);
        var cashBalance = calculator.cashBalance();
        var netContributions = calculator.netContributions();
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
                roundMoney(marketValue),
                roundMoney(costBasis),
                roundMoney(cashBalance),
                roundMoney(portfolioValue),
                roundMoney(netContributions),
                roundMoney(dividends),
                roundMoney(realizedGain),
                roundMoney(unrealizedGain),
                roundMoney(totalGain),
                returnRate(totalGain, totalPurchases),
                issues.isEmpty() && unpriced == 0 && foreign == 0,
                unpriced,
                foreign,
                positions,
                issues);
    }

    private Map<String, MarketInstrument> instrumentsByTicker(java.util.Set<String> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        return instrumentRepository.findByTickerIn(tickers).stream()
                .collect(Collectors.toMap(MarketInstrument::getTicker, Function.identity()));
    }

    private BigDecimal allocationRate(PortfolioPositionResponse position, BigDecimal totalMarketValue) {
        if (position.foreignCurrency()
                || position.marketValue() == null
                || totalMarketValue.signum() == 0) {
            return null;
        }
        return position.marketValue().divide(totalMarketValue, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private Comparator<PortfolioPositionResponse> positionComparator() {
        return Comparator.comparing(PortfolioPositionResponse::closed)
                .thenComparing(
                        PortfolioPositionResponse::marketValue,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PortfolioPositionResponse::ticker);
    }

}
