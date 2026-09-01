import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import type { PortfolioReport, PortfolioReportSchedule } from '../report/api'
import { reportApi } from '../report/api'
import { ReportsPage } from './ReportsPage'

vi.mock('../portfolio/PortfolioProvider', () => ({
  usePortfolio: () => ({ activePortfolio: { id: 1, name: 'Portafolio principal' } }),
}))

vi.mock('../report/api', () => ({
  reportApi: {
    list: vi.fn(),
    schedule: vi.fn(),
    generate: vi.fn(),
    imageUrl: (id: number, download = false) => `/api/reports/${id}/image${download ? '?download=true' : ''}`,
    pdfUrl: (id: number) => `/api/reports/${id}/pdf`,
  },
}))

/** Comprueba la vista previa, las descargas y el estado pendiente de WhatsApp. */
describe('ReportsPage', () => {
  beforeEach(() => {
    vi.mocked(reportApi.list).mockResolvedValue([report])
    vi.mocked(reportApi.schedule).mockResolvedValue(schedule)
  })

  it('muestra el último informe generado y su programación semanal', async () => {
    render(<ReportsPage />)

    expect(await screen.findByRole('heading', { name: 'Informe seleccionado' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Informe del portafolio/ })).toHaveAttribute('src', '/api/reports/7/image')
    expect(screen.getByRole('link', { name: /PNG/ })).toHaveAttribute('href', '/api/reports/7/image?download=true')
    expect(screen.getByRole('link', { name: /PDF/ })).toHaveAttribute('href', '/api/reports/7/pdf')
    expect(screen.getByText('Viernes · 5:30 p. m.')).toBeInTheDocument()
    expect(screen.getByText('WhatsApp pendiente de configuración')).toBeInTheDocument()
  })
})

const report: PortfolioReport = {
  id: 7,
  portfolioId: 1,
  portfolioName: 'Portafolio principal',
  from: '2026-08-24',
  to: '2026-08-28',
  valuationDate: '2026-08-28',
  baseCurrency: 'COP',
  triggerType: 'MANUAL',
  status: 'READY',
  generatedBy: 'admin',
  valuationComplete: true,
  provisionalPrices: 0,
  imageSize: 100000,
  pdfSize: 120000,
  deliveryStatus: 'NOT_CONFIGURED',
  deliveryMessage: null,
  errorMessage: null,
  generatedAt: '2026-08-31T20:00:00Z',
  createdAt: '2026-08-31T20:00:00Z',
}

const schedule: PortfolioReportSchedule = {
  enabled: true,
  dayOfWeek: 'FRIDAY',
  runTime: '17:30',
  timezone: 'America/Bogota',
  nextRunAt: '2026-09-04T17:30:00-05:00',
  deliveryConfigured: false,
  deliveryChannel: 'WHATSAPP_BUSINESS',
}
