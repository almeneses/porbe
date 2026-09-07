package com.porbe.app.report;

import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.portfolio.PortfolioHistoryService;
import com.porbe.app.portfolio.PortfolioService;
import com.porbe.app.portfolio.PortfolioWeeklyPositionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calcula un resumen reproducible para cualquier rango solicitado. */
@Service
public class PortfolioReportCalculator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 8;

    private final PortfolioHistoryService historyService;
    private final PortfolioService portfolioService;
    private final PortfolioOperationRepository operationRepository;
    private final MarketInstrumentRepository instrumentRepository;
    private final Clock clock;
    
    public PortfolioReportCalculator(
            PortfolioHistoryService historyService,
            PortfolioService portfolioService,
            PortfolioOperationRepository operationRepository,
            MarketInstrumentRepository instrumentRepository,
            Clock clock) {
        this.historyService = historyService;
        this.portfolioService = portfolioService;
        this.operationRepository = operationRepository;
        this.instrumentRepository = instrumentRepository;
        this.clock = clock;
    }

    /**
     * Usa el viernes anterior como línea base y el último viernes incluido como
     * cierre. Los movimientos conservan las fechas exactas elegidas por el usuario.
     */
    @Transactional(readOnly = true)
    public PortfolioReportData calculate(Long portfolioId, LocalDate from, LocalDate to) {
        validateDates(from, to);
        var endFriday = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        var history = historyService.weeklyHistory(portfolioId, null, endFriday);
        if (history.weeks().isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay cierres semanales disponibles para generar el informe seleccionado.");
        }

        var end = history.weeks().stream()
                .filter(week -> !week.weekEnding().isAfter(endFriday))
                .reduce((first, second) -> second)
                .orElseThrow();
        var prior = history.weeks().stream()
                .filter(week -> week.weekEnding().isBefore(from))
                .reduce((first, second) -> second);
        var baseline = prior.orElse(history.weeks().getFirst());
        var hasPriorBaseline = prior.isPresent();

        var portfolio = portfolioService.getPortfolio(portfolioId);
        var operations = operationRepository
                .findAllByPortfolioAndDateBetweenOrderByDateAscIdAsc(portfolio, from, to);
        var externalCashFlow = operations.stream()
                .filter(operation -> operation.getType() == OperationType.DEPOSITO
                        || operation.getType() == OperationType.RETIRO)
                .map(this::cashImpact)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var baselineValue = hasPriorBaseline ? baseline.portfolioValue() : BigDecimal.ZERO;
        var periodGain = end.portfolioValue().subtract(baselineValue).subtract(externalCashFlow);
        var periodReturn = hasPriorBaseline
                ? relativeTwr(baseline.timeWeightedReturn(), end.timeWeightedReturn())
                : safe(end.timeWeightedReturn());

        var baselinePositions = baseline.positions().stream()
                .collect(Collectors.toMap(PortfolioWeeklyPositionResponse::ticker, Function.identity()));
        var icons = iconsByTicker(end.positions(), operations);
        var periodImpact = periodImpactHighlights(baselinePositions, end.positions(), icons);
        var displayedMovements = recentMovements(operations, icons);
        var chart = history.weeks().stream()
                .filter(week -> !week.weekEnding().isAfter(end.weekEnding()))
                .map(week -> new PortfolioReportChartPoint(
                        week.weekEnding(), week.portfolioValue(), week.netContributions()))
                .toList();
        var gainsByAsset = assetValues(end.positions(), PortfolioWeeklyPositionResponse::totalGain);
        var dividendsByAsset = assetValues(end.positions(), PortfolioWeeklyPositionResponse::dividends);
        var assetAllocation = assetAllocation(end.positions(), icons);
        var sectorAllocation = sectorAllocation(end.positions());
        var provisionalPrices = (int) end.positions().stream()
                .filter(position -> position.quantity().signum() != 0 && position.provisionalPrice())
                .count();

        return new PortfolioReportData(
                portfolio.getId(),
                portfolio.getName(),
                from,
                to,
                baseline.weekEnding(),
                end.weekEnding(),
                history.baseCurrency().toUpperCase(Locale.ROOT),
                money(periodGain),
                rate(periodReturn),
                periodImpact.best(),
                periodImpact.worst(),
                end.dividends(),
                end.totalGain(),
                end.returnRate(),
                end.timeWeightedReturn(),
                end.netContributions(),
                end.cashBalance(),
                end.portfolioValue(),
                operations.size(),
                displayedMovements,
                chart,
                gainsByAsset,
                dividendsByAsset,
                assetAllocation,
                sectorAllocation,
                end.valuationComplete() && provisionalPrices == 0,
                provisionalPrices,
                end.unpricedPositions());
    }

    /** Replica los gráficos del resumen: hasta cinco montos, ordenados por impacto absoluto. */
    private List<PortfolioReportAssetValue> assetValues(
            List<PortfolioWeeklyPositionResponse> positions,
            Function<PortfolioWeeklyPositionResponse, BigDecimal> extractor) {
        return positions.stream()
                .map(position -> new PortfolioReportAssetValue(
                        position.ticker(), position.name(), extractor.apply(position)))
                .filter(value -> value.amount() != null && value.amount().signum() != 0)
                .sorted(Comparator.comparing(
                        (PortfolioReportAssetValue value) -> value.amount().abs()).reversed())
                .limit(5)
                .toList();
    }

    public PortfolioReportData calculate(LocalDate from, LocalDate to) {
        return calculate(null, from, to);
    }

    /**
     * Compara la ganancia contable de cada acción al inicio y al final. Así el
     * informe explica en pesos qué acción ayudó o redujo más el resultado.
     */
    private Highlights periodImpactHighlights(
            Map<String, PortfolioWeeklyPositionResponse> baseline,
            List<PortfolioWeeklyPositionResponse> endPositions,
            Map<String, byte[]> icons) {
        var endByTicker = endPositions.stream()
                .collect(Collectors.toMap(PortfolioWeeklyPositionResponse::ticker, Function.identity()));
        var tickers = java.util.stream.Stream.concat(baseline.keySet().stream(), endByTicker.keySet().stream())
                .collect(Collectors.toSet());
        var values = tickers.stream()
                .map(ticker -> periodImpact(ticker, baseline.get(ticker), endByTicker.get(ticker), icons.get(ticker)))
                .filter(Objects::nonNull)
                .toList();
        var comparator = Comparator.comparing(PortfolioReportAssetHighlight::amount);
        return new Highlights(
                values.stream().filter(value -> value.amount().signum() > 0).max(comparator).orElse(null),
                values.stream().filter(value -> value.amount().signum() < 0).min(comparator).orElse(null));
    }

    private PortfolioReportAssetHighlight periodImpact(
            String ticker,
            PortfolioWeeklyPositionResponse start,
            PortfolioWeeklyPositionResponse end,
            byte[] icon) {
        if ((start == null || start.totalGain() == null) && (end == null || end.totalGain() == null)) {
            return null;
        }
        var impact = safe(end == null ? null : end.totalGain()).subtract(safe(start == null ? null : start.totalGain()));
        var priceChange = start == null || end == null || start.closePrice() == null || end.closePrice() == null
                || start.closePrice().signum() == 0
                ? BigDecimal.ZERO
                : end.closePrice().divide(start.closePrice(), RATE_SCALE, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE);
        var name = end != null ? end.name() : start.name();
        return new PortfolioReportAssetHighlight(ticker, name, rate(priceChange), money(impact), icon);
    }

    private List<PortfolioReportAllocation> assetAllocation(
            List<PortfolioWeeklyPositionResponse> positions,
            Map<String, byte[]> icons) {
        var candidates = positions.stream()
                .filter(this::includedInAllocation)
                .map(position -> new AllocationCandidate(
                        position.ticker(), position.name(), position.marketValue(), icons.get(position.ticker())))
                .sorted(Comparator.comparing(AllocationCandidate::value).reversed())
                .toList();
        return allocations(candidates, "Otras acciones");
    }

    private List<PortfolioReportAllocation> sectorAllocation(List<PortfolioWeeklyPositionResponse> positions) {
        var grouped = positions.stream()
                .filter(this::includedInAllocation)
                .collect(Collectors.groupingBy(
                        position -> position.sector() == null || position.sector().isBlank()
                                ? "Sin clasificar"
                                : position.sector(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                PortfolioWeeklyPositionResponse::marketValue,
                                BigDecimal::add)));
        var candidates = grouped.entrySet().stream()
                .map(entry -> new AllocationCandidate(entry.getKey(), entry.getKey(), entry.getValue(), null))
                .sorted(Comparator.comparing(AllocationCandidate::value).reversed())
                .toList();
        return allocations(candidates, "Otros sectores");
    }

    private boolean includedInAllocation(PortfolioWeeklyPositionResponse position) {
        return !position.foreignCurrency()
                && position.quantity().signum() != 0
                && position.marketValue() != null
                && position.marketValue().signum() > 0;
    }

    /** Conserva los cuatro grupos principales y reúne el resto para mantener el informe legible. */
    private List<PortfolioReportAllocation> allocations(
            List<AllocationCandidate> candidates,
            String remainderName) {
        var total = candidates.stream().map(AllocationCandidate::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return List.of();
        }
        var result = new ArrayList<PortfolioReportAllocation>();
        candidates.stream().limit(4).forEach(candidate -> result.add(new PortfolioReportAllocation(
                candidate.key(),
                candidate.name(),
                rate(candidate.value().divide(total, RATE_SCALE, RoundingMode.HALF_UP)),
                candidate.icon())));
        if (candidates.size() > 4) {
            var remainder = candidates.stream().skip(4)
                    .map(AllocationCandidate::value)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new PortfolioReportAllocation(
                    "OTROS", remainderName,
                    rate(remainder.divide(total, RATE_SCALE, RoundingMode.HALF_UP)), null));
        }
        return List.copyOf(result);
    }

    private List<PortfolioReportMovement> recentMovements(
            List<PortfolioOperation> operations,
            Map<String, byte[]> icons) {
        var recent = new ArrayList<>(operations);
        java.util.Collections.reverse(recent);
        return recent.stream().limit(4).map(operation -> new PortfolioReportMovement(
                operation.getDate(),
                operation.getType().label(),
                operation.getTicker(),
                operation.getName(),
                operation.getQuantity(),
                operation.getTotalAmount(),
                operation.getTicker() == null ? null : icons.get(operation.getTicker().toUpperCase(Locale.ROOT))))
                .toList();
    }

    /** Carga una sola vez los íconos necesarios para que el renderizado no consulte la base de datos. */
    private Map<String, byte[]> iconsByTicker(
            List<PortfolioWeeklyPositionResponse> positions,
            List<PortfolioOperation> operations) {
        var tickers = java.util.stream.Stream.concat(
                        positions.stream().map(PortfolioWeeklyPositionResponse::ticker),
                        operations.stream().map(PortfolioOperation::getTicker).filter(java.util.Objects::nonNull))
                .map(ticker -> ticker.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (tickers.isEmpty()) {
            return Map.of();
        }
        return instrumentRepository.findByTickerIn(tickers).stream()
                .filter(MarketInstrument::hasIcon)
                .collect(Collectors.toMap(MarketInstrument::getTicker, MarketInstrument::getIconData));
    }

    private BigDecimal relativeTwr(BigDecimal baseline, BigDecimal end) {
        var baselineGrowth = BigDecimal.ONE.add(safe(baseline));
        if (baselineGrowth.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ONE.add(safe(end))
                .divide(baselineGrowth, RATE_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Selecciona las dos fechas del informe.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        var today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        if (to.isAfter(today)) {
            throw new IllegalArgumentException("La fecha final no puede estar en el futuro.");
        }
    }

    private BigDecimal cashImpact(PortfolioOperation operation) {
        return operation.getTotalAmount().multiply(BigDecimal.valueOf(operation.getType().cashSign()));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal value) {
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private record Highlights(
            PortfolioReportAssetHighlight best,
            PortfolioReportAssetHighlight worst) {
    }

    private record AllocationCandidate(
            String key,
            String name,
            BigDecimal value,
            byte[] icon) {
    }
}
