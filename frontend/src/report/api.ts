import { apiRequest } from '../auth/api'

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
