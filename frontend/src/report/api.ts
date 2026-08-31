import { apiRequest } from '../auth/api'

/** Informe persistido y formatos disponibles para descarga. */
export interface PortfolioReport {
  id: number
  from: string
  to: string
  valuationDate: string
  baseCurrency: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: 'GENERATING' | 'READY' | 'FAILED'
  generatedBy: string
  valuationComplete: boolean
  provisionalPrices: number
  imageSize: number
  pdfSize: number
  deliveryStatus: 'NOT_CONFIGURED' | 'PENDING' | 'SENT' | 'FAILED'
  deliveryMessage: string | null
  errorMessage: string | null
  generatedAt: string | null
  createdAt: string
}

/** Configuración fija del informe semanal ejecutado por Spring. */
export interface PortfolioReportSchedule {
  enabled: boolean
  dayOfWeek: string
  runTime: string
  timezone: string
  nextRunAt: string | null
  deliveryConfigured: boolean
  deliveryChannel: string
}

export const reportApi = {
  list: () => apiRequest<PortfolioReport[]>('/api/reports'),
  schedule: () => apiRequest<PortfolioReportSchedule>('/api/reports/schedule'),
  generate: (from: string, to: string) => apiRequest<PortfolioReport>('/api/reports', {
    method: 'POST',
    body: JSON.stringify({ from, to }),
  }),
  imageUrl: (id: number, download = false) => `/api/reports/${id}/image${download ? '?download=true' : ''}`,
  pdfUrl: (id: number) => `/api/reports/${id}/pdf`,
}
