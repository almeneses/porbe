import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { PortfolioPosition, PortfolioSummary } from '../portfolio/api'
import { formatCurrency, formatPercentage } from '../utils/formatters'

interface ChartItem {
  label: string
  detail?: string
  value: number
  rate: number
  color: string
}

const COLORS = ['#6f5bd3', '#268bd2', '#2d896c', '#e76f51', '#d3a52f', '#9b4dca', '#0f9d8a', '#d1495b']

/** Gráficos compactos de composición, ganancias y dividendos del dashboard. */
export function PortfolioAnalyticsCharts({ summary }: { summary: PortfolioSummary }) {
  const { t } = useTranslation()
  const assets = useMemo(() => allocationByAsset(summary.positions), [summary.positions])
  const sectors = useMemo(() => allocationBySector(summary.positions), [summary.positions])
  const gains = useMemo(() => performanceItems(summary.positions, 'gain'), [summary.positions])
  const dividends = useMemo(() => performanceItems(summary.positions, 'dividends'), [summary.positions])

  return (
    <section className="portfolio-analytics" aria-label={t('dashboard.analyticsLabel')}>
      <header className="positions-heading analytics-heading">
        <div><span className="eyebrow">{t('dashboard.analyticsEyebrow')}</span><h2>{t('dashboard.analyticsTitle')}</h2></div>
        <span>{t('dashboard.analyticsSubtitle')}</span>
      </header>
      <div className="analytics-grid">
        <DonutCard title={t('dashboard.assetComposition')} subtitle={t('dashboard.assetCompositionBody')} items={assets} currency={summary.baseCurrency} />
        <DonutCard title={t('dashboard.sectorComposition')} subtitle={t('dashboard.sectorCompositionBody')} items={sectors} currency={summary.baseCurrency} />
        <BarCard title={t('dashboard.gainsByAsset')} subtitle={t('dashboard.gainsByAssetBody')} items={gains} currency={summary.baseCurrency} signed />
        <BarCard title={t('dashboard.dividendsByAsset')} subtitle={t('dashboard.dividendsByAssetBody')} items={dividends} currency={summary.baseCurrency} />
      </div>
    </section>
  )
}

function DonutCard({ title, subtitle, items, currency }: { title: string; subtitle: string; items: ChartItem[]; currency: string }) {
  const { t } = useTranslation()
  return (
    <article className="analytics-card">
      <header><h3>{title}</h3><p>{subtitle}</p></header>
      {items.length === 0 ? <AnalyticsEmpty /> : (
        <div className="analytics-donut">
          <div className="analytics-donut__visual" style={{ background: donutGradient(items) }} role="img" aria-label={title}>
            <span><strong>{items.length}</strong><small>{t('dashboard.chartSegments')}</small></span>
          </div>
          <ul>{items.map((item) => (
            <li key={item.label}>
              <i style={{ backgroundColor: item.color }} />
              <span><strong>{item.label}</strong><small>{item.detail ?? formatCurrency(item.value, currency)}</small></span>
              <b>{formatPercentage(item.rate)}</b>
            </li>
          ))}</ul>
        </div>
      )}
    </article>
  )
}

function BarCard({ title, subtitle, items, currency, signed = false }: { title: string; subtitle: string; items: ChartItem[]; currency: string; signed?: boolean }) {
  return (
    <article className="analytics-card">
      <header><h3>{title}</h3><p>{subtitle}</p></header>
      {items.length === 0 ? <AnalyticsEmpty /> : (
        <div className="analytics-bars">{items.map((item) => (
          <div className="analytics-bar" key={item.label}>
            <div><strong>{item.label}</strong><span className={signed ? gainClass(item.value) : ''}>{signed && item.value > 0 ? '+' : ''}{formatCurrency(item.value, currency)}</span></div>
            <span className="analytics-bar__track"><i className={signed ? gainClass(item.value) : ''} style={{ width: `${Math.max(3, item.rate * 100)}%`, backgroundColor: signed ? undefined : item.color }} /></span>
          </div>
        ))}</div>
      )}
    </article>
  )
}

function AnalyticsEmpty() {
  const { t } = useTranslation()
  return <p className="analytics-empty">{t('dashboard.analyticsEmpty')}</p>
}

function allocationByAsset(positions: PortfolioPosition[]): ChartItem[] {
  return positions
    .filter((position) => position.marketValue !== null && position.marketValue > 0 && position.allocationRate !== null)
    .sort((left, right) => right.marketValue! - left.marketValue!)
    .map((position, index) => ({
      label: position.ticker,
      detail: position.sector,
      value: position.marketValue!,
      rate: position.allocationRate!,
      color: COLORS[index % COLORS.length],
    }))
}

function allocationBySector(positions: PortfolioPosition[]): ChartItem[] {
  const totals = new Map<string, number>()
  positions
    .filter((position) => position.marketValue !== null && position.marketValue > 0 && !position.foreignCurrency)
    .forEach((position) => totals.set(position.sector, (totals.get(position.sector) ?? 0) + position.marketValue!))
  const total = [...totals.values()].reduce((sum, value) => sum + value, 0)
  return [...totals.entries()]
    .sort((left, right) => right[1] - left[1])
    .map(([label, value], index) => ({ label, value, rate: total === 0 ? 0 : value / total, color: COLORS[index % COLORS.length] }))
}

function performanceItems(positions: PortfolioPosition[], kind: 'gain' | 'dividends'): ChartItem[] {
  const values = positions
    .map((position) => ({ position, value: kind === 'gain' ? position.totalGain : position.dividends }))
    .filter((item): item is { position: PortfolioPosition; value: number } => item.value !== null && item.value !== 0)
    .sort((left, right) => Math.abs(right.value) - Math.abs(left.value))
    .slice(0, 8)
  const maximum = Math.max(1, ...values.map((item) => Math.abs(item.value)))
  return values.map((item, index) => ({
    label: item.position.ticker,
    value: item.value,
    rate: Math.abs(item.value) / maximum,
    color: COLORS[index % COLORS.length],
  }))
}

function donutGradient(items: ChartItem[]) {
  let start = 0
  const stops = items.map((item) => {
    const end = start + item.rate * 100
    const stop = `${item.color} ${start}% ${end}%`
    start = end
    return stop
  })
  return `conic-gradient(${stops.join(', ')})`
}

function gainClass(value: number) {
  return value > 0 ? 'positive' : 'negative'
}
