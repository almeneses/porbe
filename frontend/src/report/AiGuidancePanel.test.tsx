import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import type { PortfolioDefinition } from '../portfolio/PortfolioProvider'
import { AiGuidancePanel } from './AiGuidancePanel'
import { reportApi } from './api'

vi.mock('./api', () => ({ reportApi: { aiGuidance: vi.fn(), saveAiGuidance: vi.fn(), cancelAiGuidance: vi.fn() } }))
const portfolios = [{ id: 1, name: 'Primero' }, { id: 2, name: 'Segundo' }] as PortfolioDefinition[]

describe('AiGuidancePanel', () => {
  beforeEach(() => {
    vi.mocked(reportApi.aiGuidance).mockImplementation(async (id) => ({ text: id === 1 ? 'Anterior' : 'Otra', status: id === 1 ? 'USED' : 'PENDING', revision: 1, updatedAt: null }))
    vi.mocked(reportApi.saveAiGuidance).mockImplementation(async (_id, text) => ({ text, status: 'PENDING', revision: 2, updatedAt: null }))
    vi.mocked(reportApi.cancelAiGuidance).mockResolvedValue({ text: '', status: 'NONE', revision: 3, updatedAt: null })
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it('saves only explicitly, including repeated text, and cancels the pending indication', async () => {
    render(<AiGuidancePanel portfolios={portfolios} />)
    const text = await screen.findByDisplayValue('Anterior')
    expect(screen.getByText(/Utilizada\./)).toBeInTheDocument()
    expect(reportApi.saveAiGuidance).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Guardar indicación' }))
    await waitFor(() => expect(reportApi.saveAiGuidance).toHaveBeenCalledWith(1, 'Anterior'))
    expect(await screen.findByText(/Pendiente de utilizar/)).toBeInTheDocument()
    fireEvent.change(text, { target: { value: 'Texto en edición' } })
    expect(reportApi.saveAiGuidance).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar indicación pendiente' }))
    await waitFor(() => expect(reportApi.cancelAiGuidance).toHaveBeenCalledWith(1))
    expect(await screen.findByText(/Sin indicación guardada/)).toBeInTheDocument()
    expect(text).toHaveValue('')
  })

  it('loads and saves independently for the selected portfolio', async () => {
    render(<AiGuidancePanel portfolios={portfolios} />)
    await screen.findByDisplayValue('Anterior')
    fireEvent.change(screen.getByLabelText('Portafolio de la indicación'), { target: { value: '2' } })
    await screen.findByDisplayValue('Otra')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar indicación' }))
    await waitFor(() => expect(reportApi.saveAiGuidance).toHaveBeenCalledWith(2, 'Otra'))
    expect(reportApi.aiGuidance).toHaveBeenCalledWith(2)
  })
})
