import { ApiRequestError, apiRequest } from '../auth/api'

/** Error de validación asociado a una fila o al archivo completo. */
export interface ImportRowError {
  row: number | null
  field: string
  message: string
}

/** Resultado normalizado de una importación, sea exitosa o rechazada. */
export interface PortfolioImportResult {
  success: boolean
  totalRows: number
  importedRows: number
  importId: number | null
  message: string
  errors: ImportRowError[]
}

/** Operación persistida que conforma el libro de movimientos del portafolio. */
export interface PortfolioOperation {
  id: number
  date: string
  type: 'compra' | 'venta' | 'dividendo' | 'depósito' | 'retiro'
  ticker: string | null
  name: string | null
  quantity: number | null
  unitPrice: number | null
  commission: number
  totalAmount: number
  cashImpact: number
  notes: string | null
  importBatchId: number
  sourceType: 'IMPORT' | 'MANUAL'
  sourceFilename: string
  importedBy: string
  importedAt: string
  createdAt: string
  updatedAt: string
  updatedBy: string
  quantityAfter: number | null
  consistencyIssue: string | null
}

/** Colección de operaciones y su conteo total. */
export interface OperationsResponse {
  total: number
  returned: number
  operations: PortfolioOperation[]
}

export type OperationTypeCode = 'compra' | 'venta' | 'dividendo' | 'depósito' | 'retiro'

/** Datos editables enviados al crear o modificar una operación manualmente. */
export interface OperationInput {
  date: string
  type: OperationTypeCode
  ticker: string | null
  name: string | null
  quantity: number | null
  unitPrice: number | null
  commission: number
  totalAmount: number
  notes: string | null
}

/** Filtros compartidos por la consulta y la exportación del libro. */
export interface OperationFilters {
  from?: string
  to?: string
  ticker?: string
  type?: OperationTypeCode | ''
  sourceType?: 'IMPORT' | 'MANUAL' | ''
  importBatchId?: number | ''
}

/** Importación Excel disponible para reversión atómica. */
export interface OperationBatch {
  id: number
  sourceFilename: string
  importedRows: number
  currentOperations: number
  importedBy: string
  importedAt: string
}

/** Registro reciente de una modificación administrativa. */
export interface OperationAuditEntry {
  id: number
  operationId: number | null
  importBatchId: number | null
  action: 'CREATED' | 'UPDATED' | 'DELETED' | 'BATCH_REVERTED'
  username: string
  details: string
  createdAt: string
}

export interface OperationValidationPayload {
  code: 'OPERACION_INVALIDA'
  message: string
  errors: Array<{ field: keyof OperationInput; message: string }>
}

/** Posición acumulada y valorada para un ticker del portafolio. */
export interface PortfolioPosition {
  ticker: string
  name: string | null
  currency: string
  sector: string
  quantity: number
  averageCost: number | null
  costBasis: number | null
  totalPurchases: number
  lastPrice: number | null
  priceDate: string | null
  provisionalPrice: boolean
  marketValue: number | null
  allocationRate: number | null
  realizedGain: number | null
  unrealizedGain: number | null
  dividends: number
  totalGain: number | null
  returnRate: number | null
  closed: boolean
  valued: boolean
  calculationComplete: boolean
  foreignCurrency: boolean
}

/** Advertencia de consistencia detectada durante el cálculo de posiciones. */
export interface PortfolioValuationIssue {
  ticker: string
  code: string
  message: string
}

/** Resumen agregado de efectivo, posiciones y rendimiento actual. */
export interface PortfolioSummary {
  calculatedAt: string
  valuationDate: string | null
  baseCurrency: string
  operationCount: number
  openPositionCount: number
  marketValue: number
  costBasis: number
  cashBalance: number
  portfolioValue: number
  netContributions: number
  dividends: number
  realizedGain: number
  unrealizedGain: number
  totalGain: number
  returnRate: number
  valuationComplete: boolean
  unpricedPositions: number
  foreignCurrencyPositions: number
  positions: PortfolioPosition[]
  issues: PortfolioValuationIssue[]
}

/** Posición de un ticker reconstruida al cierre de una semana. */
export interface PortfolioWeeklyPosition {
  ticker: string
  name: string | null
  currency: string
  sector: string
  quantity: number
  closePrice: number | null
  priceDate: string | null
  provisionalPrice: boolean
  marketValue: number | null
  costBasis: number | null
  dividends: number
  totalGain: number | null
  valued: boolean
  calculationComplete: boolean
  foreignCurrency: boolean
}

/** Punto semanal consolidado junto con su detalle por activo. */
export interface PortfolioWeeklySnapshot {
  weekEnding: string
  marketValue: number
  investedCapital: number
  netContributions: number
  dividends: number
  cashBalance: number
  portfolioValue: number
  realizedGain: number
  unrealizedGain: number
  totalGain: number
  returnRate: number
  externalCashFlow: number
  periodReturn: number | null
  timeWeightedReturn: number
  annualizedReturn: number | null
  nominalVariation: number | null
  percentageVariation: number | null
  valuationComplete: boolean
  unpricedPositions: number
  foreignCurrencyPositions: number
  inconsistentPositions: number
  positions: PortfolioWeeklyPosition[]
}

/** Serie completa de cierres semanales disponible para el portafolio. */
export interface PortfolioHistory {
  calculatedAt: string
  baseCurrency: string
  from: string | null
  to: string | null
  lastCompletedWeek: string
  operationCount: number
  weekCount: number
  valuationComplete: boolean
  weeks: PortfolioWeeklySnapshot[]
}

/** Distingue una respuesta de validación conocida de un error HTTP genérico. */
function isImportResult(value: unknown): value is PortfolioImportResult {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PortfolioImportResult>
  return typeof candidate.success === 'boolean' && Array.isArray(candidate.errors)
}

export function isOperationValidationPayload(value: unknown): value is OperationValidationPayload {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<OperationValidationPayload>
  return candidate.code === 'OPERACION_INVALIDA' && Array.isArray(candidate.errors)
}

function operationQuery(filters: OperationFilters = {}) {
  const params = new URLSearchParams()
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.ticker) params.set('ticker', filters.ticker)
  if (filters.type) params.set('type', filters.type)
  if (filters.sourceType) params.set('sourceType', filters.sourceType)
  if (filters.importBatchId !== '' && filters.importBatchId != null) {
    params.set('importBatchId', String(filters.importBatchId))
  }
  return params.size > 0 ? `?${params.toString()}` : ''
}

/** Cliente de importaciones y consultas del libro de operaciones. */
export const portfolioApi = {
  templateUrl: '/api/portfolio-import/template',
  importFile: async (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    try {
      return await apiRequest<PortfolioImportResult>('/api/portfolio-import', {
        method: 'POST',
        body: formData,
      })
    } catch (error) {
      if (error instanceof ApiRequestError && isImportResult(error.payload)) {
        return error.payload
      }
      throw error
    }
  },
  operations: (filters: OperationFilters = {}) =>
    apiRequest<OperationsResponse>(`/api/operations${operationQuery(filters)}`),
  createOperation: (operation: OperationInput) => apiRequest<PortfolioOperation>('/api/operations', {
    method: 'POST',
    body: JSON.stringify(operation),
  }),
  updateOperation: (id: number, operation: OperationInput) =>
    apiRequest<PortfolioOperation>(`/api/operations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(operation),
    }),
  deleteOperation: (id: number) => apiRequest<void>(`/api/operations/${id}`, { method: 'DELETE' }),
  operationBatches: () => apiRequest<OperationBatch[]>('/api/operation-batches'),
  revertOperationBatch: (id: number) => apiRequest<void>(`/api/operation-batches/${id}`, { method: 'DELETE' }),
  operationAudit: () => apiRequest<OperationAuditEntry[]>('/api/operation-audit'),
  operationExportUrl: (filters: OperationFilters = {}) => `/api/operations/export${operationQuery(filters)}`,
  summary: () => apiRequest<PortfolioSummary>('/api/portfolio/summary'),
  weeklyHistory: (from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.size > 0 ? `?${params.toString()}` : ''
    return apiRequest<PortfolioHistory>(`/api/portfolio/history/weekly${query}`)
  },
}
