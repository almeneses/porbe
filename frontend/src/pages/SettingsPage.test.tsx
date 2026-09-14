import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import type { MarketDataSchedule } from '../market/api'
import { marketDataApi } from '../market/api'
import type { PortfolioReportAiSettings, PortfolioReportSchedule } from '../report/api'
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
    aiInfo: vi.fn(),
    updateAiInfo: vi.fn(),
  },
}))

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(marketDataApi.schedule).mockResolvedValue(schedule)
    vi.mocked(marketDataApi.updateSchedule).mockResolvedValue({ ...schedule, dayOfWeek: 'FRIDAY', runTime: '18:00' })
    vi.mocked(reportApi.schedule).mockResolvedValue(reportSchedule)
    vi.mocked(reportApi.updateSchedule).mockResolvedValue({ ...reportSchedule, dayOfWeek: 'MONDAY', runTime: '07:15', timezone: 'America/Lima' })
    vi.mocked(reportApi.aiInfo).mockResolvedValue(aiSettings)
    vi.mocked(reportApi.updateAiInfo).mockResolvedValue({ ...aiSettings, model: 'gpt-5.5', effort: 'medium' })
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

  it('actualiza el modelo y ajusta el esfuerzo a sus opciones disponibles', async () => {
    render(<SettingsPage />)

    const form = await screen.findByRole('form', { name: 'Comentario con IA' })
    fireEvent.change(form.querySelectorAll('select')[0], { target: { value: 'gpt-5.5' } })
    fireEvent.change(form.querySelectorAll('select')[1], { target: { value: 'medium' } })
    fireEvent.click(form.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(reportApi.updateAiInfo).toHaveBeenCalledWith({ enabled: true, model: 'gpt-5.5', effort: 'medium' }))
    expect(await screen.findByText('Configuración de IA guardada.')).toBeInTheDocument()
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

const aiSettings: PortfolioReportAiSettings = {
  enabled: true,
  model: 'gpt-5.4-mini',
  effort: 'low',
  catalogAvailable: true,
  models: [
    { model: 'gpt-5.4-mini', name: 'GPT-5.4 Mini', defaultEffort: 'low', efforts: ['low', 'medium', 'high'] },
    { model: 'gpt-5.5', name: 'GPT-5.5', defaultEffort: 'medium', efforts: ['low', 'medium', 'high', 'xhigh'] },
  ],
}
