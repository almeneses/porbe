import {
  AlertTriangle,
  CalendarRange,
  CandlestickChart,
  CircleDollarSign,
  Landmark,
  LoaderCircle,
  TrendingUp,
  WalletCards,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { PortfolioHistoryChart } from '../components/PortfolioHistoryChart'
import { marketDataApi } from '../market/api'
import type { MarketTickerWeeklyCloses, MarketWeeklyCloses } from '../market/api'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioHistory, PortfolioWeeklyPosition, PortfolioWeeklySnapshot } from '../portfolio/api'
import { formatCurrency, formatDate, formatPercentage, formatQuantity } from '../utils/formatters'

/** Presenta la evolución semanal consolidada y el detalle histórico por activo. */
export function HistoryPage() {
  const { t } = useTranslation()
  const [history, setHistory] = useState<PortfolioHistory | null>(null)
  const [selectedWeek, setSelectedWeek] = useState('')
  const [marketCloses, setMarketCloses] = useState<MarketWeeklyCloses | null>(null)
  const [marketClosesError, setMarketClosesError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    portfolioApi.weeklyHistory()
      .then((response) => {
        if (!active) return
        setHistory(response)
        setSelectedWeek(response.weeks.at(-1)?.weekEnding ?? '')
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : t('history.loadError'))
      })
    return () => { active = false }
  }, [t])

  useEffect(() => {
    let active = true
    marketDataApi.weeklyCloses()
      .then((response) => { if (active) setMarketCloses(response) })
      .catch(() => { if (active) setMarketClosesError(t('history.marketClosesError')) })
    return () => { active = false }
  }, [t])

  if (error) {
    return <div className="page-state page-state--error"><AlertTriangle size={26} /><p>{error}</p></div>
  }
  if (!history) {
    return <div className="page-state"><LoaderCircle className="spin" size={28} /><p>{t('history.loading')}</p></div>
  }

  const latest = history.weeks.at(-1)
  const selected = history.weeks.find((week) => week.weekEnding === selectedWeek) ?? latest

  return (
    <div className="history-page page-enter">
      <header className="page-heading history-heading">
        <div>
          <span className="eyebrow">{t('history.eyebrow')}</span>
          <h1>{t('history.title')}</h1>
          <p>{t('history.subtitle')}</p>
        </div>
        {latest && <div className="system-pill"><span />{t('history.closedAt', { date: formatDate(latest.weekEnding) })}</div>}
      </header>

      {history.weeks.length === 0 ? (
        <HistoryEmpty hasOperations={history.operationCount > 0} />
      ) : (
        <>
          {latest && !latest.valuationComplete && <HistoryWarning week={latest} currency={history.baseCurrency} />}
          {latest && <HistoryMetrics week={latest} currency={history.baseCurrency} />}

          <section className="history-chart-card">
            <div className="card-heading history-card-heading">
              <div><h2>{t('history.chartTitle')}</h2><p>{t('history.chartSubtitle')}</p></div>
              <span className="history-week-count">{t('history.weekCount', { count: history.weekCount })}</span>
            </div>
            <PortfolioHistoryChart weeks={history.weeks} currency={history.baseCurrency} defaultRange={26} />
          </section>

          <WeeklyHistorySection weeks={history.weeks} currency={history.baseCurrency} />
          {selected && (
            <WeeklyAssetsSection
              history={history}
              week={selected}
              selectedWeek={selectedWeek}
              onWeekChange={setSelectedWeek}
            />
          )}
        </>
      )}
      <MarketWeeklyClosesSection data={marketCloses} error={marketClosesError} />
    </div>
  )
}

/** Resume los principales valores del último cierre semanal disponible. */
function HistoryMetrics({ week, currency }: { week: PortfolioWeeklySnapshot; currency: string }) {
  const { t } = useTranslation()
  const metrics = [
    { label: 'portfolioValue', value: formatCurrency(week.portfolioValue, currency), icon: WalletCards, tone: 'violet' },
    { label: 'timeWeightedReturn', value: formatPercentage(week.timeWeightedReturn), icon: TrendingUp, tone: 'green' },
    { label: 'annualizedReturn', value: week.annualizedReturn == null ? '—' : formatPercentage(week.annualizedReturn), icon: Landmark, tone: 'blue' },
    {
      label: 'totalGain',
      value: formatCurrency(week.totalGain, currency),
      detail: t('history.estimatedReturn', { value: formatPercentage(week.returnRate) }),
      icon: TrendingUp,
      tone: 'green',
    },
    { label: 'cashBalance', value: formatCurrency(week.cashBalance, currency), icon: CircleDollarSign, tone: 'coral' },
    { label: 'dividends', value: formatCurrency(week.dividends, currency), icon: CandlestickChart, tone: 'violet' },
  ]
  return (
    <section className="metric-grid" aria-label={t('history.latestSummary')}>
      {metrics.map(({ label, value, detail, icon: Icon, tone }) => (
        <article className="metric-card" key={label}>
          <span className={`metric-card__icon metric-card__icon--${tone}`}><Icon size={20} /></span>
          <span className="metric-card__label">{t(`history.${label}`)}</span>
          <strong>{value}</strong>
          <small>{detail ?? t('history.weekEnding', { date: formatDate(week.weekEnding) })}</small>
        </article>
      ))}
    </section>
  )
}

/** Señala datos faltantes o monedas que no entran en el total consolidado. */
function HistoryWarning({ week, currency }: { week: PortfolioWeeklySnapshot; currency: string }) {
  const { t } = useTranslation()
  return (
    <section className="portfolio-alert" aria-label={t('history.partialTitle')}>
      <AlertTriangle size={20} />
      <div>
        <strong>{t('history.partialTitle')}</strong>
        {week.unpricedPositions > 0 && <p>{t('history.unpricedWarning', { count: week.unpricedPositions })}</p>}
        {week.foreignCurrencyPositions > 0 && <p>{t('history.foreignWarning', { count: week.foreignCurrencyPositions, currency })}</p>}
        {week.inconsistentPositions > 0 && <p>{t('history.inconsistentWarning', { count: week.inconsistentPositions })}</p>}
      </div>
      {week.unpricedPositions > 0 && <Link to="/mercado">{t('history.updatePrices')}</Link>}
    </section>
  )
}

/** Tabla cronológica de los agregados semanales, con tarjetas equivalentes en móvil. */
function WeeklyHistorySection({ weeks, currency }: { weeks: PortfolioWeeklySnapshot[]; currency: string }) {
  const { t } = useTranslation()
  const descending = [...weeks].reverse()
  return (
    <section className="history-section">
      <header className="history-section-heading">
        <div><span className="eyebrow">{t('history.weeklyEyebrow')}</span><h2>{t('history.weeklyTitle')}</h2></div>
        <CalendarRange size={22} />
      </header>
      <div className="history-table-wrap">
        <table className="history-table">
          <thead><tr>
            <th>{t('history.date')}</th>
            <th>{t('history.portfolioValue')}</th>
            <th>{t('history.investedCapital')}</th>
            <th>{t('history.dividends')}</th>
            <th>{t('history.cashBalance')}</th>
            <th>{t('history.totalGain')}</th>
            <th>{t('history.returnRate')}</th>
            <th>{t('history.timeWeightedReturn')}</th>
            <th>{t('history.annualizedReturn')}</th>
            <th>{t('history.variation')}</th>
          </tr></thead>
          <tbody>{descending.map((week) => (
            <tr key={week.weekEnding}>
              <td><time dateTime={week.weekEnding}>{formatDate(week.weekEnding)}</time></td>
              <td className="numeric-cell"><strong>{formatCurrency(week.portfolioValue, currency)}</strong></td>
              <td className="numeric-cell">{formatCurrency(week.investedCapital, currency)}</td>
              <td className="numeric-cell">{formatCurrency(week.dividends, currency)}</td>
              <td className="numeric-cell">{formatCurrency(week.cashBalance, currency)}</td>
              <td className={`numeric-cell ${gainClass(week.totalGain)}`}>{formatCurrency(week.totalGain, currency)}</td>
              <td className={`numeric-cell ${gainClass(week.returnRate)}`}>{formatPercentage(week.returnRate)}</td>
              <td className={`numeric-cell ${gainClass(week.timeWeightedReturn)}`}>{formatPercentage(week.timeWeightedReturn)}</td>
              <td className={`numeric-cell ${gainClass(week.annualizedReturn)}`}>{week.annualizedReturn == null ? '—' : formatPercentage(week.annualizedReturn)}</td>
              <td className={`numeric-cell ${gainClass(week.nominalVariation)}`}>
                {week.nominalVariation == null
                  ? '—'
                  : <><strong>{signedCurrency(week.nominalVariation, currency)}</strong><small>{formatPercentage(week.percentageVariation ?? 0)}</small></>}
              </td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      <div className="history-week-cards">{descending.map((week) => (
        <article className="history-week-card" key={week.weekEnding}>
          <div className="history-week-card__top">
            <time dateTime={week.weekEnding}>{formatDate(week.weekEnding)}</time>
            <span className={week.valuationComplete ? 'history-status history-status--complete' : 'history-status'}>
              {t(week.valuationComplete ? 'history.complete' : 'history.partial')}
            </span>
          </div>
          <strong>{formatCurrency(week.portfolioValue, currency)}</strong>
          <dl>
            <div><dt>{t('history.investedCapital')}</dt><dd>{formatCurrency(week.investedCapital, currency)}</dd></div>
            <div><dt>{t('history.cashBalance')}</dt><dd>{formatCurrency(week.cashBalance, currency)}</dd></div>
            <div><dt>{t('history.totalGain')}</dt><dd className={gainClass(week.totalGain)}>{formatCurrency(week.totalGain, currency)}</dd></div>
            <div><dt>{t('history.returnRate')}</dt><dd className={gainClass(week.returnRate)}>{formatPercentage(week.returnRate)}</dd></div>
            <div><dt>{t('history.timeWeightedReturn')}</dt><dd className={gainClass(week.timeWeightedReturn)}>{formatPercentage(week.timeWeightedReturn)}</dd></div>
            <div><dt>{t('history.annualizedReturn')}</dt><dd className={gainClass(week.annualizedReturn)}>{week.annualizedReturn == null ? '—' : formatPercentage(week.annualizedReturn)}</dd></div>
            <div><dt>{t('history.variation')}</dt><dd className={gainClass(week.nominalVariation)}>{week.nominalVariation == null ? '—' : signedCurrency(week.nominalVariation, currency)}</dd></div>
          </dl>
        </article>
      ))}</div>
    </section>
  )
}

/** Permite recorrer cada semana y consultar cantidad, cierre y valor por ticker. */
function WeeklyAssetsSection({
  history,
  week,
  selectedWeek,
  onWeekChange,
}: {
  history: PortfolioHistory
  week: PortfolioWeeklySnapshot
  selectedWeek: string
  onWeekChange: (week: string) => void
}) {
  const { t } = useTranslation()
  return (
    <section className="history-section history-assets-section">
      <header className="history-section-heading history-assets-heading">
        <div><span className="eyebrow">{t('history.assetsEyebrow')}</span><h2>{t('history.assetsTitle')}</h2></div>
        <label className="week-selector">
          <span>{t('history.selectWeek')}</span>
          <select value={selectedWeek} onChange={(event) => onWeekChange(event.target.value)}>
            {[...history.weeks].reverse().map((option) => (
              <option key={option.weekEnding} value={option.weekEnding}>{formatDate(option.weekEnding)}</option>
            ))}
          </select>
        </label>
      </header>
      {week.positions.length === 0 ? (
        <div className="history-assets-empty"><CandlestickChart size={25} /><p>{t('history.noAssetsThisWeek')}</p></div>
      ) : (
        <>
          <div className="history-assets-table-wrap">
            <table className="history-assets-table">
              <thead><tr>
                <th>{t('history.asset')}</th>
                <th>{t('dashboard.sector')}</th>
                <th>{t('history.quantity')}</th>
                <th>{t('history.weeklyClose')}</th>
                <th>{t('history.priceDate')}</th>
                <th>{t('history.marketValue')}</th>
                <th>{t('history.assetGain')}</th>
              </tr></thead>
              <tbody>{week.positions.map((position) => <WeeklyAssetRow key={position.ticker} position={position} />)}</tbody>
            </table>
          </div>
          <div className="history-asset-cards">{week.positions.map((position) => (
            <WeeklyAssetCard key={position.ticker} position={position} />
          ))}</div>
        </>
      )}
    </section>
  )
}

function WeeklyAssetRow({ position }: { position: PortfolioWeeklyPosition }) {
  return (
    <tr>
      <td><div className="history-asset"><strong>{position.ticker}</strong><small>{position.name ?? '—'}</small><AssetStatus position={position} /></div></td>
      <td>{position.sector}</td>
      <td className="numeric-cell">{formatQuantity(position.quantity)}</td>
      <td className="numeric-cell">{moneyOrDash(position.closePrice, position.currency)}</td>
      <td className="numeric-cell">{position.priceDate ? formatDate(position.priceDate) : '—'}</td>
      <td className="numeric-cell"><strong>{moneyOrDash(position.marketValue, position.currency)}</strong></td>
      <td className={`numeric-cell ${gainClass(position.totalGain)}`}>{moneyOrDash(position.totalGain, position.currency)}</td>
    </tr>
  )
}

function WeeklyAssetCard({ position }: { position: PortfolioWeeklyPosition }) {
  const { t } = useTranslation()
  return (
    <article className="history-asset-card">
      <div className="history-asset-card__top"><div className="history-asset"><strong>{position.ticker}</strong><small>{position.name ?? '—'}</small></div><AssetStatus position={position} /></div>
      <div className="history-asset-card__value"><span>{t('history.marketValue')}</span><strong>{moneyOrDash(position.marketValue, position.currency)}</strong></div>
      <dl>
        <div><dt>{t('history.quantity')}</dt><dd>{formatQuantity(position.quantity)}</dd></div>
        <div><dt>{t('dashboard.sector')}</dt><dd>{position.sector}</dd></div>
        <div><dt>{t('history.weeklyClose')}</dt><dd>{moneyOrDash(position.closePrice, position.currency)}</dd></div>
        <div><dt>{t('history.priceDate')}</dt><dd>{position.priceDate ? formatDate(position.priceDate) : '—'}</dd></div>
        <div><dt>{t('history.assetGain')}</dt><dd className={gainClass(position.totalGain)}>{moneyOrDash(position.totalGain, position.currency)}</dd></div>
      </dl>
    </article>
  )
}

function AssetStatus({ position }: { position: PortfolioWeeklyPosition }) {
  const { t } = useTranslation()
  const state = !position.calculationComplete
    ? 'issue'
    : position.foreignCurrency
      ? 'foreign'
      : position.quantity === 0
        ? 'closed'
        : !position.valued
          ? 'unpriced'
          : position.provisionalPrice
            ? 'provisional'
            : 'valued'
  return <span className={`position-status position-status--${state}`}>{t(`dashboard.positionStatus.${state}`)}</span>
}

function HistoryEmpty({ hasOperations }: { hasOperations: boolean }) {
  const { t } = useTranslation()
  return (
    <section className="operations-empty">
      <span><CalendarRange size={26} /></span>
      <h2>{t(hasOperations ? 'history.noWeeksTitle' : 'history.emptyTitle')}</h2>
      <p>{t(hasOperations ? 'history.noWeeksBody' : 'history.emptyBody')}</p>
      <Link className="secondary-button" to={hasOperations ? '/mercado' : '/importar'}>
        {t(hasOperations ? 'history.updatePrices' : 'history.emptyAction')}
      </Link>
    </section>
  )
}

/** Consulta todos los viernes de un ticker sin depender de que existiera posición esa semana. */
function MarketWeeklyClosesSection({ data, error }: { data: MarketWeeklyCloses | null; error: string | null }) {
  const { t } = useTranslation()
  const [selectedTicker, setSelectedTicker] = useState('')
  const selected = data?.tickers.find((ticker) => ticker.ticker === selectedTicker) ?? data?.tickers[0]
  const rows = selected ? [...selected.closes].reverse() : []

  useEffect(() => {
    if (data?.tickers.length && !selectedTicker) setSelectedTicker(data.tickers[0].ticker)
  }, [data, selectedTicker])

  return (
    <section className="history-section market-closes-section">
      <header className="history-section-heading history-assets-heading">
        <div><span className="eyebrow">{t('history.marketClosesEyebrow')}</span><h2>{t('history.marketClosesTitle')}</h2><p>{t('history.marketClosesBody')}</p></div>
        {data && data.tickers.length > 0 && <label className="week-selector"><span>{t('history.selectTicker')}</span><select value={selected?.ticker ?? ''} onChange={(event) => setSelectedTicker(event.target.value)}>{data.tickers.map((ticker) => <option key={ticker.ticker} value={ticker.ticker}>{ticker.ticker}</option>)}</select></label>}
      </header>
      {error && <div className="inline-alert inline-alert--error"><AlertTriangle size={17} /><span>{error}</span></div>}
      {!data && !error && <div className="history-assets-empty"><LoaderCircle className="spin" size={22} /><p>{t('history.marketClosesLoading')}</p></div>}
      {data && data.tickers.length === 0 && <div className="history-assets-empty"><CandlestickChart size={22} /><p>{t('history.marketClosesEmpty')}</p></div>}
      {selected && <WeeklyCloseBook ticker={selected} rows={rows} />}
    </section>
  )
}

function WeeklyCloseBook({ ticker, rows }: { ticker: MarketTickerWeeklyCloses; rows: MarketTickerWeeklyCloses['closes'] }) {
  const { t } = useTranslation()
  return <>
    <div className="market-close-summary"><strong>{ticker.ticker}</strong><span>{ticker.name ?? '—'}</span><small>{ticker.sector} · {t('history.closeCount', { count: rows.length })}</small></div>
    <div className="history-market-table-wrap scrollable-table"><table className="history-market-table"><thead><tr><th>{t('history.date')}</th><th>{t('history.weeklyClose')}</th><th>{t('history.priceDate')}</th><th>{t('history.closeStatus')}</th></tr></thead><tbody>{rows.map((point) => <tr key={point.weekEnding}><td>{formatDate(point.weekEnding)}</td><td className="numeric-cell"><strong>{point.closePrice == null ? '—' : formatCurrency(point.closePrice, ticker.currency ?? 'COP')}</strong></td><td className="numeric-cell">{point.priceDate ? formatDate(point.priceDate) : '—'}</td><td className="numeric-cell"><span className={`history-status${point.closePrice !== null && !point.provisional ? ' history-status--complete' : ''}`}>{t(point.closePrice == null ? 'history.closeMissing' : point.provisional ? 'history.closeProvisional' : 'history.closeFinal')}</span></td></tr>)}</tbody></table></div>
  </>
}

function moneyOrDash(value: number | null, currency: string) {
  return value == null ? '—' : formatCurrency(value, currency)
}

function signedCurrency(value: number, currency: string) {
  return `${value > 0 ? '+' : ''}${formatCurrency(value, currency)}`
}

function gainClass(value: number | null) {
  if (value == null || value === 0) return ''
  return value > 0 ? 'positive' : 'negative'
}
