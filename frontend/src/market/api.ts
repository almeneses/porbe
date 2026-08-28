import { apiRequest } from '../auth/api'

/** Estado consolidado de precios para un activo del portafolio. */
export interface MarketTickerStatus {
  ticker: string
  name: string | null
  currency: string | null
  exchange: string | null
  firstOperationDate: string
  lastPriceDate: string | null
  lastClose: number | null
  provisional: boolean
  storedDays: number
  lastSyncedAt: string | null
}

/** Cobertura completa disponible en la fuente de mercado configurada. */
export interface MarketDataStatus {
  source: string
  checkedAt: string
  tickers: MarketTickerStatus[]
}

/** Resultado individual de una sincronización de precios. */
export interface TickerSyncResult {
  ticker: string
  success: boolean
  storedPrices: number
  message: string
}

/** Resumen agregado de la sincronización solicitada por el usuario. */
export interface MarketDataSyncResult {
  totalTickers: number
  successfulTickers: number
  storedPrices: number
  message: string
  results: TickerSyncResult[]
}

/** Cliente de los endpoints de consulta y sincronización de mercado. */
export const marketDataApi = {
  status: () => apiRequest<MarketDataStatus>('/api/market-data'),
  sync: () => apiRequest<MarketDataSyncResult>('/api/market-data/sync', { method: 'POST' }),
}
