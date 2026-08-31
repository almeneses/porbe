import {
  AlertTriangle,
  ArrowRight,
  CalendarClock,
  ChartSpline,
  CircleDollarSign,
  Landmark,
  LoaderCircle,
  PiggyBank,
  Sparkles,
  WalletCards,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { ApiRequestError } from '../auth/api'
import { PortfolioHistoryChart } from '../components/PortfolioHistoryChart'
import { PortfolioAnalyticsCharts } from '../components/PortfolioAnalyticsCharts'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioHistory, PortfolioPosition, PortfolioSummary } from '../portfolio/api'
import { formatCurrency, formatDate, formatPercentage, formatQuantity } from '../utils/formatters'

/** Presenta la valoración actual, alertas y posiciones calculadas del portafolio. */
export function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [summary, setSummary] = useState<PortfolioSummary | null>(null)
  const [history, setHistory] = useState<PortfolioHistory | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    portfolioApi.summary()
      .then((response) => { if (active) setSummary(response) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : t('dashboard.loadError'))
      })
    return () => { active = false }
  }, [t])

  useEffect(() => {
    let active = true
    portfolioApi.weeklyHistory()
      .then((response) => { if (active) setHistory(response) })
      .catch(() => { if (active) setHistoryError(t('dashboard.historyLoadError')) })
    return () => { active = false }
  }, [t])

  const performance = useMemo(() => {
    const valued = summary?.positions.filter((position) => position.returnRate !== null) ?? []
    if (valued.length === 0) return { best: null, worst: null }
    return {
      best: valued.reduce((current, position) => position.returnRate! > current.returnRate! ? position : current),
      worst: valued.reduce((current, position) => position.returnRate! < current.returnRate! ? position : current),
    }
  }, [summary])

  const hasOperations = Boolean(summary?.operationCount)
  const currency = summary?.baseCurrency ?? 'COP'
  const latestWeek = history?.weeks.at(-1)
  const metrics = [
    {
      key: 'portfolioValue',
      value: summary ? formatCurrency(summary.portfolioValue, currency) : '—',
      detail: summary?.valuationDate ? t('dashboard.valuedAt', { date: formatDate(summary.valuationDate) }) : t('dashboard.noValuationDate'),
      icon: WalletCards,
      tone: 'violet',
    },
    {
      key: 'investedCapital',
      value: summary ? formatCurrency(summary.costBasis, currency) : '—',
      detail: t('dashboard.openPositions', { count: summary?.openPositionCount ?? 0 }),
      icon: Landmark,
      tone: 'blue',
    },
    {
      key: 'timeWeightedReturn',
      value: latestWeek ? formatPercentage(latestWeek.timeWeightedReturn) : '—',
      detail: t('dashboard.timeWeightedReturnDetail'),
      icon: ChartSpline,
      tone: 'green',
    },
    {
      key: 'annualizedReturn',
      value: latestWeek?.annualizedReturn == null ? '—' : formatPercentage(latestWeek.annualizedReturn),
      detail: t('dashboard.annualizedReturnDetail'),
      icon: CalendarClock,
      tone: 'blue',
    },
    {
      key: 'availableCash',
      value: summary ? formatCurrency(summary.cashBalance, currency) : '—',
      detail: summary ? t('dashboard.netContributions', { value: formatCurrency(summary.netContributions, currency) }) : t('dashboard.calculating'),
      icon: CircleDollarSign,
      tone: 'coral',
    },
    {
      key: 'dividends',
      value: summary ? formatCurrency(summary.dividends, currency) : '—',
      detail: t('dashboard.dividendsDetail'),
      icon: Sparkles,
      tone: 'violet',
    },
  ]

  return (
    <div className="dashboard page-enter">
      <header className="page-heading">
        <div>
          <span className="eyebrow">{t('dashboard.eyebrow')}</span>
          <h1>{t('dashboard.greeting', { name: user?.username })}</h1>
          <p>{t('dashboard.subtitle')}</p>
        </div>
        <div className="system-pill"><span /> {t('dashboard.status')}</div>
      </header>

      {error && <div className="portfolio-alert portfolio-alert--error"><AlertTriangle size={19} /><span>{error}</span></div>}

      <section className="metric-grid" aria-label={t('dashboard.summaryLabel')}>
        {metrics.map(({ key, value, detail, icon: Icon, tone }) => (
          <article className="metric-card" key={key}>
            <span className={`metric-card__icon metric-card__icon--${tone}`}><Icon size={20} /></span>
            <span className="metric-card__label">{t(`dashboard.${key}`)}</span>
            <strong>{value}</strong>
            <small>{summary ? detail : t('dashboard.calculating')}</small>
          </article>
        ))}
      </section>

      {summary && hasOperations && !summary.valuationComplete && <ValuationAlert summary={summary} />}

      <section className="dashboard-grid">
        <article className="chart-card">
          <div className="card-heading">
            <div>
              <h2>{t('dashboard.chartTitle')}</h2>
              <p>{t('dashboard.chartSubtitle')}</p>
            </div>
            {history && history.weeks.length > 0 && (
              <Link className="chart-detail-link" to="/historico">{t('dashboard.viewHistory')} <ArrowRight size={14} /></Link>
            )}
          </div>
          {summary && history && history.weeks.length > 0 ? (
            <PortfolioHistoryChart weeks={history.weeks} currency={history.baseCurrency} defaultRange={26} />
          ) : (
            <div className="empty-chart">
              <div className="empty-chart__grid" />
              <div className="empty-chart__content">
                {!summary || (!history && !historyError) ? (
                  <><LoaderCircle className="spin" size={26} /><p>{t('dashboard.calculating')}</p></>
                ) : historyError ? (
                  <>
                    <span className="empty-chart__icon"><AlertTriangle size={25} /></span>
                    <h3>{t('dashboard.chartUnavailableTitle')}</h3>
                    <p>{historyError}</p>
                    <Link className="secondary-button" to="/historico">{t('dashboard.retryInHistory')}</Link>
                  </>
                ) : (
                  <>
                    <span className="empty-chart__icon"><PiggyBank size={25} /></span>
                    <h3>{t(hasOperations ? 'dashboard.readyTitle' : 'dashboard.emptyTitle')}</h3>
                    <p>{t(hasOperations ? 'dashboard.readyBody' : 'dashboard.emptyBody', {
                      count: summary.operationCount,
                      positions: summary.openPositionCount,
                    })}</p>
                    <Link className="secondary-button" to={hasOperations ? (summary.valuationComplete ? '/historico' : '/mercado') : '/importar'}>
                      {t(hasOperations ? (summary.valuationComplete ? 'dashboard.viewHistory' : 'dashboard.readyAction') : 'dashboard.emptyAction')}
                    </Link>
                  </>
                )}
              </div>
            </div>
          )}
        </article>

        <aside className="foundation-card">
          <span className="foundation-card__icon"><Sparkles size={23} /></span>
          <span className="eyebrow">{t('dashboard.performanceEyebrow')}</span>
          <h2>{performance.best?.ticker ?? t('dashboard.noPerformanceTitle')}</h2>
          <p>{performance.best
            ? t('dashboard.bestResult', { value: formatPercentage(performance.best.returnRate!) })
            : t('dashboard.noPerformanceBody')}</p>
          <ul>
            <li><span /> {t('dashboard.worstResult')}: <strong>{performance.worst?.ticker ?? '—'}</strong></li>
            <li><span /> {t('dashboard.dividends')}: <strong>{summary ? formatCurrency(summary.dividends, currency) : '—'}</strong></li>
            <li><span /> {t('dashboard.realizedGain')}: <strong>{summary ? formatCurrency(summary.realizedGain, currency) : '—'}</strong></li>
            <li><span /> {t('dashboard.unrealizedGain')}: <strong>{summary ? formatCurrency(summary.unrealizedGain, currency) : '—'}</strong></li>
          </ul>
        </aside>
      </section>

      {summary && summary.positions.length > 0 && <PortfolioAnalyticsCharts summary={summary} />}
      {summary && summary.positions.length > 0 && <PositionsSection summary={summary} />}
    </div>
  )
}

/** Explica por qué el total mostrado es parcial y cómo completarlo. */
function ValuationAlert({ summary }: { summary: PortfolioSummary }) {
  const { t } = useTranslation()
  return (
    <section className="portfolio-alert" aria-label={t('dashboard.partialTitle')}>
      <AlertTriangle size={20} />
      <div>
        <strong>{t('dashboard.partialTitle')}</strong>
        {summary.unpricedPositions > 0 && <p>{t('dashboard.unpricedWarning', { count: summary.unpricedPositions })}</p>}
        {summary.foreignCurrencyPositions > 0 && <p>{t('dashboard.foreignWarning', { count: summary.foreignCurrencyPositions, currency: summary.baseCurrency })}</p>}
        {summary.issues.map((issue) => <p key={`${issue.ticker}-${issue.code}`}><b>{issue.ticker}:</b> {issue.message}</p>)}
      </div>
      {summary.unpricedPositions > 0 && <Link to="/mercado">{t('dashboard.updatePrices')} <ArrowRight size={15} /></Link>}
    </section>
  )
}

/** Renderiza las posiciones en tabla para PC y tarjetas para pantallas pequeñas. */
function PositionsSection({ summary }: { summary: PortfolioSummary }) {
  const { t } = useTranslation()
  return (
    <section className="positions-section">
      <header className="positions-heading">
        <div><span className="eyebrow">{t('dashboard.positionsEyebrow')}</span><h2>{t('dashboard.positionsTitle')}</h2></div>
        <span>{t('dashboard.positionCount', { count: summary.positions.length })}</span>
      </header>
      <div className="positions-table-wrap">
        <table className="positions-table">
          <thead><tr>
            <th>{t('dashboard.asset')}</th>
            <th>{t('dashboard.sector')}</th>
            <th>{t('dashboard.quantity')}</th>
            <th>{t('dashboard.averageCost')}</th>
            <th>{t('dashboard.lastPrice')}</th>
            <th>{t('dashboard.marketValue')}</th>
            <th>{t('dashboard.gain')}</th>
            <th>{t('dashboard.return')}</th>
          </tr></thead>
          <tbody>{summary.positions.map((position) => <PositionRow key={position.ticker} position={position} />)}</tbody>
        </table>
      </div>
      <div className="position-cards">{summary.positions.map((position) => <PositionCard key={position.ticker} position={position} />)}</div>
    </section>
  )
}

/** Fila de posición para la presentación tabular de escritorio. */
function PositionRow({ position }: { position: PortfolioPosition }) {
  return (
    <tr>
      <td><PositionAsset position={position} /></td>
      <td>{position.sector}</td>
      <td className="numeric-cell">{formatQuantity(position.quantity)}</td>
      <td className="numeric-cell">{moneyOrDash(position.averageCost, position.currency)}</td>
      <td className="numeric-cell">{moneyOrDash(position.lastPrice, position.currency)}</td>
      <td className="numeric-cell"><strong>{moneyOrDash(position.marketValue, position.currency)}</strong></td>
      <td className={`numeric-cell ${gainClass(position.totalGain)}`}>{moneyOrDash(position.totalGain, position.currency)}</td>
      <td className={`numeric-cell ${gainClass(position.returnRate)}`}>{position.returnRate == null ? '—' : formatPercentage(position.returnRate)}</td>
    </tr>
  )
}

/** Tarjeta equivalente para mantener legible la posición en móvil. */
function PositionCard({ position }: { position: PortfolioPosition }) {
  const { t } = useTranslation()
  return (
    <article className="position-card">
      <div className="position-card__top"><PositionAsset position={position} /></div>
      <div className="position-card__value"><span>{t('dashboard.marketValue')}</span><strong>{moneyOrDash(position.marketValue, position.currency)}</strong></div>
      <dl>
        <div><dt>{t('dashboard.quantity')}</dt><dd>{formatQuantity(position.quantity)}</dd></div>
        <div><dt>{t('dashboard.sector')}</dt><dd>{position.sector}</dd></div>
        <div><dt>{t('dashboard.averageCost')}</dt><dd>{moneyOrDash(position.averageCost, position.currency)}</dd></div>
        <div><dt>{t('dashboard.lastPrice')}</dt><dd>{moneyOrDash(position.lastPrice, position.currency)}</dd></div>
        <div><dt>{t('dashboard.gain')}</dt><dd className={gainClass(position.totalGain)}>{moneyOrDash(position.totalGain, position.currency)}</dd></div>
      </dl>
    </article>
  )
}

/** Identificación compacta del activo y estado de su valoración. */
function PositionAsset({ position }: { position: PortfolioPosition }) {
  return (
    <div className="position-asset">
      <strong>{position.ticker}</strong>
      <small>{position.name ?? '—'}</small>
      <PositionStatus position={position} />
    </div>
  )
}

/** Selecciona la etiqueta más relevante para el estado de una posición. */
function PositionStatus({ position }: { position: PortfolioPosition }) {
  const { t } = useTranslation()
  const state = !position.calculationComplete
    ? 'issue'
    : position.foreignCurrency
      ? 'foreign'
      : position.closed
        ? 'closed'
        : !position.valued
          ? 'unpriced'
          : position.provisionalPrice
            ? 'provisional'
            : 'valued'
  return <span className={`position-status position-status--${state}`}>{t(`dashboard.positionStatus.${state}`)}</span>
}

function moneyOrDash(value: number | null, currency: string) {
  return value == null ? '—' : formatCurrency(value, currency)
}

function gainClass(value: number | null) {
  if (value == null || value === 0) return ''
  return value > 0 ? 'positive' : 'negative'
}
