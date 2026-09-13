import { apiRequest } from '../auth/api'
import type { WeekDay } from '../market/api'

/** Informe persistido y formatos disponibles para descarga. */
export interface PortfolioReport {
  id: number
  portfolioId: number
  portfolioName: string
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

/** Programación semanal persistida del informe. */
export interface PortfolioReportSchedule {
  enabled: boolean
  dayOfWeek: WeekDay
  runTime: string
  timezone: string
  nextRunAt: string | null
  lastRunAt: string | null
  lastRunStatus: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | null
  lastRunMessage: string | null
  updatedBy: string
  updatedAt: string
  deliveryConfigured: boolean
  deliveryChannel: string
}

/** Estado de la sesión administrada por whatsapp-web.js en el contenedor Node. */
export interface WhatsAppConnectionStatus {
  state: 'DISABLED' | 'STARTING' | 'QR_REQUIRED' | 'AUTHENTICATING' | 'READY' | 'AUTH_FAILURE' | 'DISCONNECTED' | 'FAILED' | 'UNAVAILABLE'
  ready: boolean
  qrDataUrl: string | null
  accountLabel: string | null
  message: string
  updatedAt: string | null
}

export const reportApi = {
  list: (portfolioId: number) => apiRequest<PortfolioReport[]>(`/api/reports?portfolioId=${portfolioId}`),
  schedule: () => apiRequest<PortfolioReportSchedule>('/api/reports/schedule'),
  updateSchedule: (schedule: Pick<PortfolioReportSchedule, 'enabled' | 'dayOfWeek' | 'runTime' | 'timezone'>) =>
    apiRequest<PortfolioReportSchedule>('/api/reports/schedule', {
      method: 'PUT',
      body: JSON.stringify(schedule),
    }),
  aiInfo: () => apiRequest<{ model: string, effort: string }>('/api/reports/ai-info'),
  whatsAppStatus: () => apiRequest<WhatsAppConnectionStatus>('/api/reports/whatsapp/status'),
  generate: (portfolioId: number, from: string, to: string) => apiRequest<PortfolioReport>('/api/reports', {
    method: 'POST',
    body: JSON.stringify({ portfolioId, from, to }),
  }),
  imageUrl: (id: number, download = false) => `/api/reports/${id}/image${download ? '?download=true' : ''}`,
  pdfUrl: (id: number) => `/api/reports/${id}/pdf`,
  sendByWhatsApp: (id: number, recipient: string) => apiRequest<PortfolioReport>(`/api/reports/${id}/whatsapp`, {
    method: 'POST',
    body: JSON.stringify({ recipient }),
  }),
}
