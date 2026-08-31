import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import '../i18n'
import type { PortfolioWeeklySnapshot } from '../portfolio/api'
import { formatDate } from '../utils/formatters'
import { PortfolioHistoryChart } from './PortfolioHistoryChart'

/** Verifica los rangos y la selección accesible de puntos del gráfico semanal. */
describe('PortfolioHistoryChart', () => {
  it('muestra el rango inicial y permite seleccionar otra semana', () => {
    const weeks = weeklySnapshots(30)
    const { container } = render(<PortfolioHistoryChart weeks={weeks} currency="COP" />)

    expect(container.querySelectorAll('.chart-hit-area')).toHaveLength(26)

    const firstVisiblePoint = container.querySelector<SVGRectElement>('.chart-hit-area')
    expect(firstVisiblePoint).not.toBeNull()
    fireEvent.focus(firstVisiblePoint!)
    expect(screen.getByText(formatDate(weeks[4].weekEnding), { selector: 'time' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '12 sem' }))
    expect(container.querySelectorAll('.chart-hit-area')).toHaveLength(12)
  })
})

function weeklySnapshots(count: number): PortfolioWeeklySnapshot[] {
  const start = Date.UTC(2025, 0, 3)
  return Array.from({ length: count }, (_, index) => ({
    weekEnding: new Date(start + index * 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
    marketValue: 900_000 + index * 20_000,
    investedCapital: 800_000,
    netContributions: 1_000_000,
    dividends: 10_000,
    cashBalance: 100_000,
    portfolioValue: 1_000_000 + index * 20_000,
    realizedGain: 0,
    unrealizedGain: index * 20_000,
    totalGain: 10_000 + index * 20_000,
    returnRate: index / 100,
    externalCashFlow: index === 0 ? 1_000_000 : 0,
    periodReturn: index === 0 ? 0 : 0.02,
    timeWeightedReturn: index / 100,
    annualizedReturn: index === 0 ? null : index / 10,
    nominalVariation: index === 0 ? null : 20_000,
    percentageVariation: index === 0 ? null : 0.02,
    valuationComplete: true,
    unpricedPositions: 0,
    foreignCurrencyPositions: 0,
    inconsistentPositions: 0,
    positions: [],
  }))
}
