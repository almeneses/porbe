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
}

/** Colección de operaciones y su conteo total. */
export interface OperationsResponse {
  total: number
  operations: PortfolioOperation[]
}

/** Distingue una respuesta de validación conocida de un error HTTP genérico. */
function isImportResult(value: unknown): value is PortfolioImportResult {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PortfolioImportResult>
  return typeof candidate.success === 'boolean' && Array.isArray(candidate.errors)
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
  operations: () => apiRequest<OperationsResponse>('/api/operations'),
}
