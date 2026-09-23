import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import '../i18n'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioHistory, PortfolioSummary, PortfolioWeeklySnapshot } from '../portfolio/api'
import { DashboardPage } from './DashboardPage'

vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ user: { username: 'Prueba' } }) }))
vi.mock('../portfolio/PortfolioProvider', () => ({ usePortfolio: () => ({ activePortfolio: { id: 1 } }) }))
vi.mock('../portfolio/api', () => ({ portfolioApi: { summary: vi.fn(), weeklyHistory: vi.fn() } }))
vi.mock('../components/PortfolioHistoryChart', () => ({ PortfolioHistoryChart: () => null }))

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('MWR en Resumen', () => {
  it('distingue el total desde el inicio, el acumulado del año y la tasa anualizada', async () => {
    renderDashboard(0.09119857, 0.37336254, 0.89)
    const yearly = (await screen.findByText('MWR del año')).closest('article')!
    const annualized = screen.getByText('MWR anualizado').closest('article')!
    expect(await within(yearly).findByText('9,12%')).toBeInTheDocument()
    expect(within(yearly).getByText(/Acumulado de 2026 al/)).toBeInTheDocument()
    expect(within(annualized).getByText('37,34%')).toBeInTheDocument()
    expect(within(annualized).getByText(/desde el primer aporte/)).toBeInTheDocument()
    const total = screen.getByText('MWR total').closest('article')!
    expect(within(total).getByText('89,0%')).toBeInTheDocument()
    expect(within(total).getByText(/Acumulado desde el primer aporte al/)).toBeInTheDocument()
  })

  it('muestra una tasa no disponible como guion y nunca como cero', async () => {
    renderDashboard(null, null, null)
    const yearly = (await screen.findByText('MWR del año')).closest('article')!
    expect(await within(yearly).findByText('Sin datos suficientes o sin una tasa única calculable')).toBeInTheDocument()
    expect(within(yearly).getByText('—')).toBeInTheDocument()
    expect(within(yearly).queryByText('0,00%')).not.toBeInTheDocument()
    const total = screen.getByText('MWR total').closest('article')!
    expect(within(total).getByText('—')).toBeInTheDocument()
    expect(within(total).getByText('Sin datos suficientes o sin una tasa única calculable')).toBeInTheDocument()
  })
})

function renderDashboard(yearMoneyWeightedReturn: number | null, annualizedMoneyWeightedReturn: number | null, totalMoneyWeightedReturn: number | null) {
  const summary: PortfolioSummary = {
    calculatedAt: '2026-09-18T20:00:00Z', valuationDate: '2026-09-18', baseCurrency: 'COP',
    operationCount: 1, openPositionCount: 0, marketValue: 0, costBasis: 0, cashBalance: 1000,
    portfolioValue: 1000, netContributions: 1000, dividends: 0, realizedGain: 0, unrealizedGain: 0,
    totalGain: 0, returnRate: 0, valuationComplete: true, unpricedPositions: 0,
    foreignCurrencyPositions: 0, issues: [], positions: [],
  }
  const history: PortfolioHistory = {
    calculatedAt: summary.calculatedAt, baseCurrency: 'COP', from: '2025-01-03', to: '2026-09-18',
    lastCompletedWeek: '2026-09-18', operationCount: 1, weekCount: 1, valuationComplete: true,
    yearMoneyWeightedReturn, annualizedMoneyWeightedReturn, totalMoneyWeightedReturn,
    weeks: [{ weekEnding: '2026-09-18', timeWeightedReturn: 0.2, annualizedReturn: 0.1 } as PortfolioWeeklySnapshot],
  }
  vi.mocked(portfolioApi.summary).mockResolvedValue(summary)
  vi.mocked(portfolioApi.weeklyHistory).mockResolvedValue(history)
  render(<MemoryRouter><DashboardPage /></MemoryRouter>)
}
