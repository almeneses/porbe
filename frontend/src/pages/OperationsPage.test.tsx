import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import '../i18n'
import { OperationsPage } from './OperationsPage'

vi.mock('../portfolio/PortfolioProvider', () => ({ usePortfolio: () => ({ activePortfolio: { id: 7 } }) }))
vi.mock('../portfolio/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../portfolio/api')>()
  return { portfolioApi: {
    ...original.portfolioApi,
    operations: vi.fn().mockResolvedValue({ total: 0, returned: 0, operations: [] }),
    operationBatches: vi.fn().mockResolvedValue([]),
    operationAudit: vi.fn().mockResolvedValue([]),
  } }
})

afterEach(cleanup)

it('exports the whole active portfolio even when filters are applied', async () => {
  render(<MemoryRouter><OperationsPage /></MemoryRouter>)
  await screen.findByText('Todavía no hay operaciones')
  expect(screen.getByRole('link', { name: 'Exportar portafolio' }))
    .toHaveAttribute('href', '/api/operations/export?portfolioId=7')
  expect(screen.queryByRole('link', { name: 'Exportar filtrados' })).not.toBeInTheDocument()

  fireEvent.change(screen.getByPlaceholderText('ECOPETROL.CL'), { target: { value: 'ECOPETROL.CL' } })
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  expect(screen.getByRole('link', { name: 'Exportar portafolio' }))
    .toHaveAttribute('href', '/api/operations/export?portfolioId=7')
  expect(screen.getByRole('link', { name: 'Exportar filtrados' }))
    .toHaveAttribute('href', '/api/operations/export?portfolioId=7&ticker=ECOPETROL.CL')
})
