import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '../i18n'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioOperation } from '../portfolio/api'
import { OperationFormDialog } from './OperationFormDialog'

/** Verifica el cálculo asistido y el payload de una compra manual. */
describe('OperationFormDialog', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })
  it('calcula y crea una operación con los valores diligenciados', async () => {
    const create = vi.spyOn(portfolioApi, 'createOperation')
      .mockResolvedValue({} as PortfolioOperation)
    const onSaved = vi.fn()

    render(<OperationFormDialog portfolioId={1} operation={null} onClose={vi.fn()} onSaved={onSaved} />)
    fireEvent.change(screen.getByLabelText('Ticker Yahoo Finance'), { target: { value: 'ecopetrol.cl' } })
    fireEvent.change(screen.getByLabelText('Nombre del activo'), { target: { value: 'Ecopetrol' } })
    fireEvent.change(screen.getByLabelText('Cantidad'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Precio unitario'), { target: { value: '100' } })
    fireEvent.change(screen.getByLabelText('Comisión'), { target: { value: '5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calcular total' }))

    expect(screen.getByLabelText('Total del movimiento')).toHaveValue(1005)
    fireEvent.click(screen.getByRole('button', { name: 'Crear operación' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
    expect(create).toHaveBeenCalledWith(1, expect.objectContaining({
      type: 'compra',
      ticker: 'ECOPETROL.CL',
      quantity: 10,
      unitPrice: 100,
      commission: 5,
      totalAmount: 1005,
    }))
    create.mockRestore()
  })
  it('registra un cambio de moneda atómico con comisión COP y sin ticker', async () => {
    const create = vi.spyOn(portfolioApi, 'createOperation').mockResolvedValue({} as PortfolioOperation)
    const onSaved = vi.fn()
    render(<OperationFormDialog portfolioId={1} operation={null} onClose={vi.fn()} onSaved={onSaved} />)
    fireEvent.change(screen.getByLabelText('Tipo de operación'), { target: { value: 'compra USD' } })
    expect(screen.queryByLabelText('Ticker Yahoo Finance')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Moneda de los importes')).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Cantidad de dólares (USD)'), { target: { value: '1000' } })
    fireEvent.change(screen.getByLabelText('Tasa COP por USD'), { target: { value: '4000' } })
    fireEvent.change(screen.getByLabelText('Comisión'), { target: { value: '10000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calcular total' }))
    expect(screen.getByLabelText('Total del movimiento')).toHaveValue(4010000)
    fireEvent.click(screen.getByRole('button', { name: 'Crear operación' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
    expect(create).toHaveBeenCalledWith(1, expect.objectContaining({ type: 'compra USD', currency: 'COP',
      ticker: null, name: null, quantity: 1000, unitPrice: 4000, commission: 10000, totalAmount: 4010000 }))
    create.mockRestore()
  })

  it('envía la moneda USD explícita al comprar acciones internacionales', async () => {
    const create = vi.spyOn(portfolioApi, 'createOperation').mockResolvedValue({} as PortfolioOperation)
    const onSaved = vi.fn()
    render(<OperationFormDialog portfolioId={1} operation={null} onClose={vi.fn()} onSaved={onSaved} />)
    fireEvent.change(screen.getByLabelText('Moneda de los importes'), { target: { value: 'USD' } })
    fireEvent.change(screen.getByLabelText('Ticker Yahoo Finance'), { target: { value: 'HIMS' } })
    fireEvent.change(screen.getByLabelText('Nombre del activo'), { target: { value: 'Hims' } })
    fireEvent.change(screen.getByLabelText('Cantidad'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Precio unitario'), { target: { value: '200' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calcular total' }))
    fireEvent.click(screen.getByRole('button', { name: 'Crear operación' }))
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
    expect(create).toHaveBeenCalledWith(1, expect.objectContaining({ type: 'compra', currency: 'USD', ticker: 'HIMS', totalAmount: 400 }))
    create.mockRestore()
  })

})
