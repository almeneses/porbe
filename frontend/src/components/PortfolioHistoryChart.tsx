import { useEffect, useId, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PortfolioWeeklySnapshot } from '../portfolio/api'
import { formatCurrency, formatDate } from '../utils/formatters'

type HistoryRange = 12 | 26 | 52 | 'all'

interface PortfolioHistoryChartProps {
  weeks: PortfolioWeeklySnapshot[]
  currency: string
  defaultRange?: HistoryRange
}

const WIDTH = 900
const HEIGHT = 310
const PADDING = { top: 20, right: 18, bottom: 40, left: 18 }

/** Gráfico SVG interactivo de valor del portafolio frente al capital aportado. */
export function PortfolioHistoryChart({
  weeks,
  currency,
  defaultRange = 26,
}: PortfolioHistoryChartProps) {
  const { t } = useTranslation()
  const gradientId = `portfolio-area-${useId().replaceAll(':', '')}`
  const [range, setRange] = useState<HistoryRange>(defaultRange)
  const visibleWeeks = useMemo(
    () => range === 'all' ? weeks : weeks.slice(-range),
    [range, weeks],
  )
  const [activeIndex, setActiveIndex] = useState(Math.max(0, visibleWeeks.length - 1))

  useEffect(() => {
    setActiveIndex(Math.max(0, visibleWeeks.length - 1))
  }, [visibleWeeks.length, range])

  const plotWidth = WIDTH - PADDING.left - PADDING.right
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom
  const maximum = Math.max(
    1,
    ...visibleWeeks.flatMap((week) => [week.portfolioValue, week.netContributions]),
  )
  const points = visibleWeeks.map((week, index) => {
    const x = visibleWeeks.length === 1
      ? PADDING.left + plotWidth / 2
      : PADDING.left + (index / (visibleWeeks.length - 1)) * plotWidth
    return {
      week,
      x,
      portfolioY: yCoordinate(week.portfolioValue, maximum, plotHeight),
      contributionsY: yCoordinate(week.netContributions, maximum, plotHeight),
    }
  })
  const active = points[activeIndex] ?? points.at(-1)
  const portfolioPath = linePath(points.map((point) => [point.x, point.portfolioY]))
  const contributionsPath = linePath(points.map((point) => [point.x, point.contributionsY]))
  const areaPath = points.length > 1
    ? `${portfolioPath} L ${points.at(-1)!.x} ${PADDING.top + plotHeight} L ${points[0].x} ${PADDING.top + plotHeight} Z`
    : ''
  const labelIndexes = [...new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])]

  return (
    <div className="portfolio-chart">
      <div className="chart-toolbar">
        <div className="chart-legend" aria-label={t('history.chartLegend')}>
          <span><i className="chart-dot chart-dot--value" />{t('history.portfolioValue')}</span>
          <span><i className="chart-dot chart-dot--contributions" />{t('history.netContributions')}</span>
        </div>
        <div className="chart-ranges" aria-label={t('history.rangeLabel')}>
          {([12, 26, 52, 'all'] as HistoryRange[]).map((option) => (
            <button
              className={range === option ? 'chart-range chart-range--active' : 'chart-range'}
              key={option}
              type="button"
              onClick={() => setRange(option)}
            >
              {t(`history.ranges.${option}`)}
            </button>
          ))}
        </div>
      </div>

      {active && (
        <div className="chart-selection" aria-live="polite">
          <time dateTime={active.week.weekEnding}>{formatDate(active.week.weekEnding)}</time>
          <span>{t('history.portfolioValue')} <strong>{formatCurrency(active.week.portfolioValue, currency)}</strong></span>
          <span>{t('history.netContributions')} <strong>{formatCurrency(active.week.netContributions, currency)}</strong></span>
        </div>
      )}

      <div className="chart-canvas">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          aria-label={t('history.chartAriaLabel')}
          preserveAspectRatio="none"
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="var(--primary)" stopOpacity=".24" />
              <stop offset="1" stopColor="var(--primary)" stopOpacity="0" />
            </linearGradient>
          </defs>
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const y = PADDING.top + ratio * plotHeight
            return <line className="chart-grid-line" key={ratio} x1={PADDING.left} x2={WIDTH - PADDING.right} y1={y} y2={y} />
          })}
          {areaPath && <path d={areaPath} fill={`url(#${gradientId})`} />}
          <path className="chart-line chart-line--contributions" d={contributionsPath} />
          <path className="chart-line chart-line--value" d={portfolioPath} />
          {active && (
            <>
              <line className="chart-active-line" x1={active.x} x2={active.x} y1={PADDING.top} y2={PADDING.top + plotHeight} />
              <circle className="chart-active-point chart-active-point--value" cx={active.x} cy={active.portfolioY} r="6" />
              <circle className="chart-active-point chart-active-point--contributions" cx={active.x} cy={active.contributionsY} r="5" />
            </>
          )}
          {points.map((point, index) => (
            <rect
              className="chart-hit-area"
              key={point.week.weekEnding}
              x={point.x - Math.max(8, plotWidth / Math.max(points.length, 1) / 2)}
              y={PADDING.top}
              width={Math.max(16, plotWidth / Math.max(points.length, 1))}
              height={plotHeight}
              tabIndex={0}
              role="button"
              aria-label={`${formatDate(point.week.weekEnding)}: ${formatCurrency(point.week.portfolioValue, currency)}`}
              onMouseEnter={() => setActiveIndex(index)}
              onFocus={() => setActiveIndex(index)}
              onTouchStart={() => setActiveIndex(index)}
            />
          ))}
          {labelIndexes.map((index) => points[index] && (
            <text
              className="chart-axis-label"
              key={points[index].week.weekEnding}
              x={points[index].x}
              y={HEIGHT - 8}
              textAnchor={index === 0 ? 'start' : index === points.length - 1 ? 'end' : 'middle'}
            >
              {formatDate(points[index].week.weekEnding)}
            </text>
          ))}
        </svg>
      </div>
    </div>
  )
}

function yCoordinate(value: number, maximum: number, plotHeight: number) {
  return PADDING.top + plotHeight - (Math.max(0, value) / maximum) * plotHeight
}

function linePath(points: Array<[number, number]>) {
  return points.map(([x, y], index) => `${index === 0 ? 'M' : 'L'} ${x} ${y}`).join(' ')
}
