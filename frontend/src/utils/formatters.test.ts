import { describe, expect, it } from 'vitest'
import { formatAmount, formatCop, formatCurrency, formatDate, formatPercentage, formatQuantity } from './formatters'

describe('formateadores colombianos', () => {
  it('presenta pesos colombianos sin decimales', () => {
    expect(formatCop(2450000)).toContain('2.450.000')
  })

  it('presenta porcentajes con decimal', () => {
    expect(formatPercentage(0.125)).toContain('12,5')
  })

  it('presenta montos y cantidades sin perder fracciones', () => {
    expect(formatAmount(1850.5)).toContain('1.850,5')
    expect(formatQuantity(1.125)).toContain('1,125')
  })

  it('presenta fechas ISO sin corrimientos de zona horaria', () => {
    expect(formatDate('2025-01-06')).toContain('2025')
  })

  it('respeta la moneda reportada por el proveedor', () => {
    expect(formatCurrency(2630, 'COP')).toContain('2.630')
    expect(formatCurrency(42.5, 'USD')).toContain('42,50')
  })
})
