import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import '../i18n'
import type { PortfolioSummary } from '../portfolio/api'
import { PortfolioAnalyticsCharts } from './PortfolioAnalyticsCharts'

/** Comprueba que las cuatro vistas analíticas usen posiciones y sectores reales. */
describe('PortfolioAnalyticsCharts', () => {
  afterEach(cleanup)

  it('muestra composición, ganancias y dividendos', () => {
    render(<PortfolioAnalyticsCharts summary={summary()} />)

    expect(screen.getByRole('heading', { name: 'Composición por activo' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Composición por sector' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ganancias por activo' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dividendos por acción' })).toBeInTheDocument()
    expect(screen.getAllByText('ECOPETROL.CL').length).toBeGreaterThan(1)
    expect(screen.getAllByText('Energía')).toHaveLength(2)
  })

  it.each([
    { gains: [100, -900, 500, 300, -10, 200, -500, null, 0], expected: ['ACTIVO-2', 'ACTIVO-3', 'ACTIVO-5', 'ACTIVO-1', 'ACTIVO-6'] },
    { gains: [10, 50, 20, 40, 30, 60], expected: ['ACTIVO-5', 'ACTIVO-1', 'ACTIVO-3', 'ACTIVO-0', 'ACTIVO-2'] },
    { gains: [10, -20], expected: ['ACTIVO-0', 'ACTIVO-1'] },
    { gains: [-10, -30, -20], expected: ['ACTIVO-1', 'ACTIVO-2'] },
    { gains: [20, 20, 20, 20, 20], expected: ['ACTIVO-0', 'ACTIVO-1', 'ACTIVO-2', 'ACTIVO-4', 'ACTIVO-3'] },
    { gains: [null, 0], expected: [] },
  ])('selecciona ganancias sin repetir activos: $gains', ({ gains, expected }) => {
    const data = summary()
    data.positions = gains.map((totalGain, index) => ({ ...data.positions[0], ticker: `ACTIVO-${index}`, totalGain }))
    render(<PortfolioAnalyticsCharts summary={data} />)

    const card = screen.getByRole('heading', { name: 'Ganancias por activo' }).closest('article')!
    expect([...card.querySelectorAll('.analytics-bar strong')].map((label) => label.textContent)).toEqual(expected)
    const dividendCard = screen.getByRole('heading', { name: 'Dividendos por acción' }).closest('article')!
    expect(within(dividendCard).getAllByText(/^ACTIVO-/)).toHaveLength(Math.min(8, gains.length))
  })
})

function summary(): PortfolioSummary {
  return {
    calculatedAt: '2026-08-31T12:00:00Z',
    valuationDate: '2026-08-28',
    baseCurrency: 'COP',
    operationCount: 3,
    openPositionCount: 1,
    marketValue: 1200,
    costBasis: 1000,
    cashBalance: 200,
    portfolioValue: 1400,
    netContributions: 1000,
    dividends: 40,
    realizedGain: 0,
    unrealizedGain: 200,
    totalGain: 240,
    returnRate: 0.24,
    valuationComplete: true,
    unpricedPositions: 0,
    foreignCurrencyPositions: 0,
    issues: [],
    positions: [{
      ticker: 'ECOPETROL.CL',
      name: 'Ecopetrol',
      currency: 'COP',
      sector: 'Energía',
      quantity: 10,
      averageCost: 100,
      costBasis: 1000,
      totalPurchases: 1000,
      lastPrice: 120,
      priceDate: '2026-08-28',
      provisionalPrice: false,
      marketValue: 1200,
      allocationRate: 1,
      realizedGain: 0,
      unrealizedGain: 200,
      dividends: 40,
      totalGain: 240,
      returnRate: 0.24,
      closed: false,
      valued: true,
      calculationComplete: true,
      foreignCurrency: false,
    }],
  }
}
