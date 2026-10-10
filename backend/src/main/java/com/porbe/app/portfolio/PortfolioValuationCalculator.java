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
    private final Function<LocalDate, MarketPriceDaily> fxAtDate;
    private final Map<String, String> currencies = new LinkedHashMap<>();
    private final PortfolioPositionLedger dollars = new PortfolioPositionLedger("COP=X");
    private boolean usesUsd;
    private BigDecimal dollarPurchasesCop = BigDecimal.ZERO;
    private BigDecimal cashBalance = BigDecimal.ZERO;
    private BigDecimal netContributions = BigDecimal.ZERO;

    PortfolioValuationCalculator(String baseCurrency, Map<String, MarketInstrument> instruments,
            Function<LocalDate, MarketPriceDaily> fxAtDate) {
        this.baseCurrency = baseCurrency;
        this.instruments = instruments;
        this.fxAtDate = fxAtDate;
    }

    void apply(PortfolioOperation operation) {
        var type = operation.getType();
        var amount = operation.getTotalAmount();
        if (type.isExchange()) {
            usesUsd = true;
            var quantity = operation.getQuantity();
            cashBalance = cashBalance.add(amount.multiply(BigDecimal.valueOf(type.cashSign())));
            if (type == OperationType.COMPRA_USD) {
                dollars.purchase(quantity, amount);
                dollarPurchasesCop = dollarPurchasesCop.add(amount);
            } else {
                dollars.sale(quantity, amount);
            }
            return;
        }
        var ticker = operation.getTicker() == null ? null : normalizeTicker(operation.getTicker());
        var usd = "USD".equals(operation.getCurrency());
        var fx = usd ? fxAtDate.apply(operation.getDate()) : null;
        var converted = usd ? fx == null ? null : amount.multiply(fx.getClose()) : amount;
        if (ticker != null && (type == OperationType.COMPRA || type == OperationType.VENTA || type == OperationType.DIVIDENDO)) {
            var ledger = ledgers.computeIfAbsent(ticker, PortfolioPositionLedger::new);
            ledger.name(operation.getName());
            var previousCurrency = currencies.putIfAbsent(ticker, operation.getCurrency());
            if (previousCurrency != null && !previousCurrency.equals(operation.getCurrency())) ledger.incomplete();
            // Keep quantities even when a historical exchange rate is missing.
            if (converted == null) ledger.incomplete();
            switch (type) {
                case COMPRA -> ledger.purchase(operation.getQuantity(), converted == null ? BigDecimal.ZERO : converted);
                case VENTA -> ledger.sale(operation.getQuantity(), converted == null ? BigDecimal.ZERO : converted);
                case DIVIDENDO -> ledger.dividend(converted == null ? BigDecimal.ZERO : converted);
                default -> { }
            }
        }
        var cashImpact = amount.multiply(BigDecimal.valueOf(type.cashSign()));
        if (usd) {
            usesUsd = true;
            if (converted == null) dollars.incomplete();
            if (cashImpact.signum() < 0) {
                dollars.sale(amount, converted == null ? BigDecimal.ZERO : converted);
            } else if (cashImpact.signum() > 0) {
                dollars.purchase(amount, converted == null ? BigDecimal.ZERO : converted);
            }
        } else if (isContribution(operation) || ticker == null || !currencyMismatch(ticker)) {
            cashBalance = cashBalance.add(cashImpact);
        }
        if (isContribution(operation)) netContributions = netContributions.add(cashImpact);
    }

    List<PortfolioPositionResponse> positions(Function<String, MarketPriceDaily> priceAtCutoff) {
        var fx = priceAtCutoff.apply("COP=X");
        var result = new java.util.ArrayList<PortfolioPositionResponse>();
        ledgers.values().forEach(ledger -> result.add(valuePosition(ledger, instruments.get(ledger.ticker()),
                priceAtCutoff.apply(ledger.ticker()), "USD".equals(currencies.get(ledger.ticker())) ? fx : null,
                "USD".equals(currencies.get(ledger.ticker())))));
        if (usesUsd) result.add(valuePosition(dollars, null, fx, null, false));
        return result;
    }

    /**
     * Construye la valoración de un ticker a partir de su contabilidad acumulada,
     * con costos históricos en COP y precios USD convertidos a la fecha de corte.
     *
     * <p>Con contabilidad completa, una posición cerrada (cantidad neta cero) no
     * necesita precio y vale cero. Si la contabilidad está incompleta o falta el precio de
     * una posición abierta, el valor de mercado y las ganancias no realizada y
     * total quedan en {@code null}. El costo y la ganancia realizada solo requieren
     * contabilidad completa.
     *
     * <p>La rentabilidad es ganancia total / compras acumuladas (cero si no hay
     * compras), expresada como fracción. Los importes se redondean a dos decimales;
     * cantidades, precios, costo promedio y rentabilidad, hasta ocho.
     *
     * @param ledger cantidades, costos, ganancias realizadas y dividendos del ticker
     * @param instrument metadatos del activo; puede ser {@code null}. Si falta la
     *                   moneda, se usa la moneda base del portafolio
     * @param latest precio suministrado para el corte; puede ser {@code null}
     * @return detalle de la posición con indicadores de cierre, valoración y moneda;
     *         la participación en el portafolio ({@code allocationRate}) queda pendiente
     */
    private PortfolioPositionResponse valuePosition(
            PortfolioPositionLedger ledger,
            MarketInstrument instrument,
            MarketPriceDaily latest,
            MarketPriceDaily fx,
            boolean usd) {
        var declaredCurrency = usd ? "USD" : baseCurrency;
        var foreignCurrency = instrument != null && instrument.getCurrency() != null
                && !declaredCurrency.equalsIgnoreCase(instrument.getCurrency());
        var currency = foreignCurrency ? instrument.getCurrency().toUpperCase(Locale.ROOT) : baseCurrency;
        var dollarPosition = ledger == dollars;
        var usablePrice = latest != null && (!usd || fx != null);
        var price = !usablePrice ? null : latest.getClose().multiply(usd ? fx.getClose() : BigDecimal.ONE);
        var closed = ledger.netQuantity().signum() == 0;
        var valued = ledger.calculationComplete() && (closed || usablePrice);
        var marketValue = valued
                ? closed ? BigDecimal.ZERO : ledger.netQuantity().multiply(price)
                : null;
        // La ganancia no realizada compara el valor de mercado con el costo aún invertido.
        var unrealizedGain = valued ? marketValue.subtract(ledger.costBasis()) : null;
        // El resultado total incluye ventas ya realizadas, posición remanente y dividendos.
        var totalGain = valued
                ? ledger.realizedGain().add(unrealizedGain).add(ledger.dividends())
                : null;

        return new PortfolioPositionResponse(
                ledger.ticker(),
                dollarPosition ? "Dólar disponible" : instrument != null && instrument.getName() != null ? instrument.getName() : ledger.name(),
                currency,
                dollarPosition ? "Efectivo" : instrument == null
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
                price == null ? null : roundValue(price),
                !usablePrice ? null : usd && fx.getPriceDate().isBefore(latest.getPriceDate()) ? fx.getPriceDate() : latest.getPriceDate(),
                latest != null && (!latest.isFinalClose() || (usd && fx != null && !fx.isFinalClose())),
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
        return sumEligibleLedgers(PortfolioPositionLedger::realizedGain).add(dollars.calculationComplete() ? dollars.realizedGain() : BigDecimal.ZERO);
    }

    BigDecimal totalPurchases() {
        return ledgers.values().stream()
                .filter(ledger -> ledger.calculationComplete() && !currencyMismatch(ledger.ticker())
                        && !"USD".equals(currencies.get(ledger.ticker())))
                .map(PortfolioPositionLedger::totalPurchases).reduce(dollarPurchasesCop, BigDecimal::add);
    }

    private BigDecimal sumEligibleLedgers(Function<PortfolioPositionLedger, BigDecimal> amount) {
        return ledgers.values().stream()
                .filter(ledger -> ledger.calculationComplete() && !currencyMismatch(ledger.ticker()))
                .map(amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean currencyMismatch(String ticker) {
        var instrument = instruments.get(ticker);
        return instrument != null && instrument.getCurrency() != null
                && !currencies.getOrDefault(ticker, baseCurrency).equalsIgnoreCase(instrument.getCurrency());
    }

    static Set<String> tickers(List<PortfolioOperation> operations) {
        var result = operations.stream().map(PortfolioOperation::getTicker).filter(Objects::nonNull)
                .map(PortfolioValuationCalculator::normalizeTicker)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (operations.stream().anyMatch(operation -> "USD".equals(operation.getCurrency()) || operation.getType().isExchange())) {
            result.add("COP=X");
        }
        return result;
    }

    static MarketPriceDaily latestPrice(NavigableMap<LocalDate, MarketPriceDaily> prices, LocalDate cutoff) {
        var entry = prices == null ? null : prices.floorEntry(cutoff);
        return entry == null ? null : entry.getValue();
    }

    static boolean isContribution(PortfolioOperation operation) {
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
