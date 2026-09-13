import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import type { MarketDataSchedule } from '../market/api'
import { marketDataApi } from '../market/api'
import { SettingsPage } from './SettingsPage'

vi.mock('../market/api', () => ({
  marketDataApi: {
    schedule: vi.fn(),
    updateSchedule: vi.fn(),
  },
}))

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(marketDataApi.schedule).mockResolvedValue(schedule)
    vi.mocked(marketDataApi.updateSchedule).mockResolvedValue({ ...schedule, dayOfWeek: 'FRIDAY', runTime: '18:00' })
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('consulta y actualiza la programación de precios desde Configuración', async () => {
    render(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: 'Datos de mercado' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Día'), { target: { value: 'FRIDAY' } })
    fireEvent.change(screen.getByLabelText('Hora'), { target: { value: '18:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar programación' }))

    await waitFor(() => expect(marketDataApi.updateSchedule).toHaveBeenCalledWith({ enabled: true, dayOfWeek: 'FRIDAY', runTime: '18:00' }))
    expect(await screen.findByText('Programación guardada.')).toBeInTheDocument()
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
