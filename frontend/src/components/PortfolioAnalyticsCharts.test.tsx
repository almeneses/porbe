import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import '../i18n'
import type { PortfolioSummary } from '../portfolio/api'
import { PortfolioAnalyticsCharts } from './PortfolioAnalyticsCharts'

/** Comprueba que las cuatro vistas analíticas usen posiciones y sectores reales. */
describe('PortfolioAnalyticsCharts', () => {
  it('muestra composición, ganancias y dividendos', () => {
    render(<PortfolioAnalyticsCharts summary={summary()} />)

    expect(screen.getByRole('heading', { name: 'Composición por acción' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Composición por sector' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ganancias por acción' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dividendos por acción' })).toBeInTheDocument()
    expect(screen.getAllByText('ECOPETROL.CL').length).toBeGreaterThan(1)
    expect(screen.getAllByText('Energía')).toHaveLength(2)
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
