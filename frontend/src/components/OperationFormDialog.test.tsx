import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import '../i18n'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioOperation } from '../portfolio/api'
import { OperationFormDialog } from './OperationFormDialog'

/** Verifica el cálculo asistido y el payload de una compra manual. */
describe('OperationFormDialog', () => {
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
})
