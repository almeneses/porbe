import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import type { MarketDataSchedule } from '../market/api'
import { marketDataApi } from '../market/api'
import type { PortfolioReportSchedule } from '../report/api'
import { reportApi } from '../report/api'
import { SettingsPage } from './SettingsPage'

vi.mock('../market/api', () => ({
  marketDataApi: {
    schedule: vi.fn(),
    updateSchedule: vi.fn(),
  },
}))

vi.mock('../report/api', () => ({
  reportApi: {
    schedule: vi.fn(),
    updateSchedule: vi.fn(),
  },
}))

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(marketDataApi.schedule).mockResolvedValue(schedule)
    vi.mocked(marketDataApi.updateSchedule).mockResolvedValue({ ...schedule, dayOfWeek: 'FRIDAY', runTime: '18:00' })
    vi.mocked(reportApi.schedule).mockResolvedValue(reportSchedule)
    vi.mocked(reportApi.updateSchedule).mockResolvedValue({ ...reportSchedule, dayOfWeek: 'MONDAY', runTime: '07:15', timezone: 'America/Lima' })
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('consulta y actualiza la programación de precios desde Configuración', async () => {
    render(<SettingsPage />)

    const section = await screen.findByRole('region', { name: 'Datos de mercado' })
    fireEvent.change(section.querySelector('select')!, { target: { value: 'FRIDAY' } })
    fireEvent.change(section.querySelector('input[type="time"]')!, { target: { value: '18:00' } })
    fireEvent.click(section.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(marketDataApi.updateSchedule).toHaveBeenCalledWith({ enabled: true, dayOfWeek: 'FRIDAY', runTime: '18:00' }))
    expect(await screen.findByText('Programación guardada.')).toBeInTheDocument()
  })

  it('actualiza día, hora y zona horaria del informe', async () => {
    render(<SettingsPage />)

    const section = await screen.findByRole('region', { name: 'Informes' })
    fireEvent.change(section.querySelector('select')!, { target: { value: 'MONDAY' } })
    fireEvent.change(section.querySelector('input[type="time"]')!, { target: { value: '07:15' } })
    fireEvent.change(section.querySelector('input[list="report-timezones"]')!, { target: { value: 'America/Lima' } })
    fireEvent.click(section.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(reportApi.updateSchedule).toHaveBeenCalledWith({ enabled: true, dayOfWeek: 'MONDAY', runTime: '07:15', timezone: 'America/Lima' }))
    expect(await screen.findByText('Programación del informe guardada.')).toBeInTheDocument()
  })
})

const schedule: MarketDataSchedule = {
  enabled: true,
  dayOfWeek: 'SATURDAY',
  runTime: '08:00',
  timezone: 'America/Bogota',
  nextRunAt: '2026-09-19T08:00:00-05:00',
  lastRunAt: null,
  lastRunStatus: null,
  lastRunMessage: null,
  updatedBy: 'admin',
  updatedAt: '2026-09-12T09:00:00-05:00',
}

const reportSchedule: PortfolioReportSchedule = {
  enabled: true,
  dayOfWeek: 'FRIDAY',
  runTime: '17:30',
  timezone: 'America/Bogota',
  nextRunAt: '2026-09-18T17:30:00-05:00',
  lastRunAt: null,
  lastRunStatus: null,
  lastRunMessage: null,
  updatedBy: 'system',
  updatedAt: '2026-09-12T09:00:00-05:00',
  deliveryConfigured: false,
  deliveryChannel: 'WHATSAPP_WEB',
}
