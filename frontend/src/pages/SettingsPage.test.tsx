import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import '../i18n'
import type { MarketDataSchedule } from '../market/api'
import { marketDataApi } from '../market/api'
import type { PortfolioReport, PortfolioReportAiSettings, PortfolioReportSchedule } from '../report/api'
import { reportApi } from '../report/api'
import { SettingsPage } from './SettingsPage'

const updateScheduledReport = vi.fn()

vi.mock('../portfolio/PortfolioProvider', () => ({
  usePortfolio: () => ({ portfolios, updateScheduledReport }),
}))

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
    whatsAppRecipients: vi.fn(),
    whatsAppStatus: vi.fn(),
    resetWhatsAppSession: vi.fn(),
    createWhatsAppRecipient: vi.fn(),
    updateWhatsAppRecipient: vi.fn(),
    deleteWhatsAppRecipient: vi.fn(),
    testWhatsAppRecipient: vi.fn(),
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
    vi.mocked(reportApi.whatsAppRecipients).mockResolvedValue(recipients)
    vi.mocked(reportApi.whatsAppStatus).mockResolvedValue(whatsAppStatus)
    vi.mocked(reportApi.createWhatsAppRecipient).mockResolvedValue({ ...recipients[0], id: 2, name: 'Familia' })
    vi.mocked(reportApi.updateWhatsAppRecipient).mockImplementation(async (id, recipient) => ({ ...recipients[0], id, ...recipient }))
    vi.mocked(reportApi.deleteWhatsAppRecipient).mockResolvedValue(undefined)
    vi.mocked(reportApi.testWhatsAppRecipient).mockResolvedValue({} as PortfolioReport)
    updateScheduledReport.mockResolvedValue({ ...portfolios[0], scheduledReportEnabled: false })
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('consulta y actualiza la programación de precios desde Configuración', async () => {
    renderPage()

    const section = await screen.findByRole('region', { name: 'Datos de mercado' })
    fireEvent.change(section.querySelector('select')!, { target: { value: 'FRIDAY' } })
    fireEvent.change(section.querySelector('input[type="time"]')!, { target: { value: '18:00' } })
    fireEvent.click(section.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(marketDataApi.updateSchedule).toHaveBeenCalledWith({ enabled: true, dayOfWeek: 'FRIDAY', runTime: '18:00' }))
    expect(await screen.findByText('Programación guardada.')).toBeInTheDocument()
  })

  it('actualiza día, hora y zona horaria del informe', async () => {
    renderPage()

    const section = await screen.findByRole('region', { name: 'Informes' })
    fireEvent.change(section.querySelector('select')!, { target: { value: 'MONDAY' } })
    fireEvent.change(section.querySelector('input[type="time"]')!, { target: { value: '07:15' } })
    fireEvent.change(section.querySelector('input[list="report-timezones"]')!, { target: { value: 'America/Lima' } })
    fireEvent.click(section.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(reportApi.updateSchedule).toHaveBeenCalledWith({ enabled: true, dayOfWeek: 'MONDAY', runTime: '07:15', timezone: 'America/Lima' }))
    expect(await screen.findByText('Programación del informe guardada.')).toBeInTheDocument()
  })

  it('actualiza el modelo y ajusta el esfuerzo a sus opciones disponibles', async () => {
    renderPage()

    const form = await screen.findByRole('form', { name: 'Comentario con IA' })
    fireEvent.change(form.querySelectorAll('select')[0], { target: { value: 'gpt-5.5' } })
    fireEvent.change(form.querySelectorAll('select')[1], { target: { value: 'medium' } })
    fireEvent.click(form.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(reportApi.updateAiInfo).toHaveBeenCalledWith({ enabled: true, model: 'gpt-5.5', effort: 'medium' }))
    expect(await screen.findByText('Configuración de IA guardada.')).toBeInTheDocument()
  })

  it('agrega un destinatario y prueba el envío del último informe', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()

    const addForm = await screen.findByRole('form', { name: 'Agregar destinatario' })
    fireEvent.change(addForm.querySelectorAll('input')[0], { target: { value: 'Familia' } })
    fireEvent.change(addForm.querySelectorAll('input')[1], { target: { value: '+57 311 000 0000' } })
    fireEvent.click(addForm.querySelector('button[type="submit"]')!)

    await waitFor(() => expect(reportApi.createWhatsAppRecipient).toHaveBeenCalledWith({ name: 'Familia', phoneNumber: '+57 311 000 0000', enabled: true }))
    fireEvent.click(within(screen.getByRole('form', { name: 'Alejo' })).getByRole('button', { name: 'Probar' }))
    await waitFor(() => expect(reportApi.testWhatsAppRecipient).toHaveBeenCalledWith(1))
    expect(await screen.findByText('Informe de prueba enviado.')).toBeInTheDocument()
  })

  it('selecciona los portafolios incluidos en el informe automático', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Portafolio principal' }))

    await waitFor(() => expect(updateScheduledReport).toHaveBeenCalledWith(1, false))
    expect(screen.getByText('1 portafolio')).toBeInTheDocument()
  })

  it('confirma el borrado, bloquea el botón mientras espera y muestra el nuevo QR conservando destinatarios', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const qrStatus = { ...whatsAppStatus, state: 'QR_REQUIRED' as const, ready: false, accountLabel: null, qrDataUrl: 'data:image/png;base64,new-qr' }
    let finishReset!: (value: typeof qrStatus) => void
    vi.mocked(reportApi.resetWhatsAppSession).mockImplementation(() => new Promise((resolve) => { finishReset = resolve }))
    renderPage()
    const button = await screen.findByRole('button', { name: 'Borrar vinculación' })
    await waitFor(() => expect(button).toBeEnabled())
    fireEvent.click(button)
    expect(reportApi.resetWhatsAppSession).not.toHaveBeenCalled()
    confirm.mockReturnValue(true)
    fireEvent.click(button)
    expect(screen.getByRole('button', { name: 'Borrando vinculación…' })).toBeDisabled()
    vi.mocked(reportApi.whatsAppStatus).mockResolvedValue(qrStatus)
    finishReset(qrStatus)
    expect(await screen.findByRole('img', { name: 'Código QR para vincular WhatsApp' })).toHaveAttribute('src', qrStatus.qrDataUrl)
    expect(screen.getByRole('form', { name: 'Alejo' })).toBeInTheDocument()
    expect(reportApi.resetWhatsAppSession).toHaveBeenCalledTimes(1)
    expect(reportApi.deleteWhatsAppRecipient).not.toHaveBeenCalled()
    expect(screen.queryByText('Cuenta vinculada: •••• 4567')).not.toBeInTheDocument()
  })

  it('muestra el error de borrado y permite reintentar', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(reportApi.resetWhatsAppSession).mockRejectedValue(new Error('unavailable'))
    renderPage()
    const button = await screen.findByRole('button', { name: 'Borrar vinculación' })
    await waitFor(() => expect(button).toBeEnabled())
    fireEvent.click(button)
    expect(await screen.findByText('No fue posible borrar la vinculación de WhatsApp. Intenta nuevamente.')).toBeInTheDocument()
    await waitFor(() => expect(button).toBeEnabled())
  })
})

function renderPage() {
  return render(<MemoryRouter><SettingsPage /></MemoryRouter>)
}

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

const recipients = [{
  id: 1,
  name: 'Alejo',
  phoneNumber: '573001234567',
  enabled: true,
  updatedBy: 'admin',
  updatedAt: '2026-09-12T09:00:00-05:00',
}]

const portfolios = [{
  id: 1,
  name: 'Portafolio principal',
  baseCurrency: 'COP',
  scheduledReportEnabled: true,
  createdAt: '2026-09-12T09:00:00-05:00',
  updatedAt: '2026-09-12T09:00:00-05:00',
}]

const whatsAppStatus = {
  state: 'READY' as const,
  ready: true,
  qrDataUrl: null,
  accountLabel: '•••• 4567',
  message: 'WhatsApp está conectado y listo para enviar.',
  updatedAt: '2026-09-12T09:00:00-05:00',
}
