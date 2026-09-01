package com.porbe.app.report;

import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.market.MarketInstrument;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.portfolio.PortfolioHistoryService;
import com.porbe.app.portfolio.PortfolioService;
import com.porbe.app.portfolio.PortfolioWeeklyPositionResponse;
import com.porbe.app.portfolio.PortfolioWeeklySnapshot;
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
        var appreciations = appreciationHighlights(baselinePositions, end.positions(), icons);
        var profitability = profitabilityHighlights(end.positions(), icons);
        var displayedMovements = recentMovements(operations, icons);
        var chart = history.weeks().stream()
                .filter(week -> !week.weekEnding().isAfter(end.weekEnding()))
                .map(week -> new PortfolioReportChartPoint(
                        week.weekEnding(), week.portfolioValue(), week.netContributions()))
                .toList();
        var sixMonthStart = end.weekEnding().minusMonths(6);
        var sixMonthChart = chart.stream()
                .filter(point -> !point.date().isBefore(sixMonthStart))
                .toList();
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
                appreciations.best(),
                appreciations.worst(),
                profitability.best(),
                profitability.worst(),
                end.dividends(),
                end.totalGain(),
                end.returnRate(),
                end.netContributions(),
                end.cashBalance(),
                end.portfolioValue(),
                operations.size(),
                displayedMovements,
                chart,
                sixMonthChart,
                end.valuationComplete() && provisionalPrices == 0,
                provisionalPrices,
                end.unpricedPositions());
    }

    public PortfolioReportData calculate(LocalDate from, LocalDate to) {
        return calculate(null, from, to);
    }

    private Highlights appreciationHighlights(
            Map<String, PortfolioWeeklyPositionResponse> baseline,
            List<PortfolioWeeklyPositionResponse> endPositions,
            Map<String, byte[]> icons) {
        var values = endPositions.stream()
                .filter(position -> position.quantity().signum() != 0 && position.closePrice() != null)
                .map(position -> {
                    var start = baseline.get(position.ticker());
                    if (start == null || start.closePrice() == null || start.closePrice().signum() == 0) {
                        return null;
                    }
                    var change = position.closePrice()
                            .divide(start.closePrice(), RATE_SCALE, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE);
                    return new PortfolioReportAssetHighlight(
                            position.ticker(), position.name(), rate(change), null, icons.get(position.ticker()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return highlights(values);
    }

    private Highlights profitabilityHighlights(
            List<PortfolioWeeklyPositionResponse> positions,
            Map<String, byte[]> icons) {
        var values = positions.stream()
                .filter(position -> position.quantity().signum() != 0
                        && position.costBasis() != null
                        && position.costBasis().signum() > 0
                        && position.totalGain() != null)
                .map(position -> new PortfolioReportAssetHighlight(
                        position.ticker(),
                        position.name(),
                        rate(position.totalGain().divide(
                                position.costBasis(), RATE_SCALE, RoundingMode.HALF_UP)),
                        money(position.totalGain()),
                        icons.get(position.ticker())))
                .toList();
        return highlights(values);
    }

    private Highlights highlights(List<PortfolioReportAssetHighlight> values) {
        var comparator = Comparator.comparing(PortfolioReportAssetHighlight::rate);
        return new Highlights(
                values.stream().max(comparator).orElse(null),
                values.stream().min(comparator).orElse(null));
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
}
