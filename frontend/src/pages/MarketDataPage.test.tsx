import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import '../i18n'
import { marketDataApi } from '../market/api'
import { MarketDataPage } from './MarketDataPage'

vi.mock('../portfolio/PortfolioProvider', () => ({
  usePortfolio: () => ({ activePortfolio: { id: 1 } }),
}))

vi.mock('../market/api', () => ({
  marketDataApi: { status: vi.fn(), iconUrl: (ticker: string) => `/api/market-data/${ticker}/icon` },
}))

afterEach(cleanup)

it('muestra los proveedores del portafolio y la fuente de cada último precio', async () => {
  const common = {
    currency: 'COP', exchange: 'BVC', sector: 'Finanzas', firstOperationDate: '2026-08-24',
    lastPriceDate: '2026-10-08', lastClose: 49500, provisional: false, storedDays: 2,
    lastSyncedAt: null, hasIcon: false, iconUpdatedAt: null,
  }
  vi.mocked(marketDataApi.status).mockResolvedValue({
    source: 'YAHOO_FINANCE, STOCK_ANALYSIS', checkedAt: '2026-10-09T21:00:00Z',
    tickers: [
      { ...common, ticker: 'NUCO.CL', name: 'Nu Holdings Ltd.', source: 'STOCK_ANALYSIS' },
      { ...common, ticker: 'ECOPETROL.CL', name: 'Ecopetrol', source: 'YAHOO_FINANCE' },
    ],
  })
  render(<MarketDataPage />)

  expect(await screen.findByText('Yahoo Finance · Stock Analysis')).toBeInTheDocument()
  expect(screen.getByText('BVC · COP · Stock Analysis')).toBeInTheDocument()
  expect(screen.getByText('BVC · COP · Yahoo Finance')).toBeInTheDocument()
})
