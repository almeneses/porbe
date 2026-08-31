import { apiRequest } from '../auth/api'

/** Estado consolidado de precios para un activo del portafolio. */
export interface MarketTickerStatus {
  ticker: string
  name: string | null
  currency: string | null
  exchange: string | null
  sector: string
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

export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

/** Programación semanal persistida en el backend. */
export interface MarketDataSchedule {
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
}

export interface MarketWeeklyClosePoint {
  weekEnding: string
  closePrice: number | null
  priceDate: string | null
  provisional: boolean
}

export interface MarketTickerWeeklyCloses {
  ticker: string
  name: string | null
  currency: string | null
  sector: string
  closes: MarketWeeklyClosePoint[]
}

/** Cierres de todos los viernes desde el 19 de enero de 2024. */
export interface MarketWeeklyCloses {
  calculatedAt: string
  from: string
  to: string
  tickerCount: number
  weekCount: number
  complete: boolean
  tickers: MarketTickerWeeklyCloses[]
}

/** Cliente de los endpoints de consulta y sincronización de mercado. */
export const marketDataApi = {
  status: () => apiRequest<MarketDataStatus>('/api/market-data'),
  sync: () => apiRequest<MarketDataSyncResult>('/api/market-data/sync', { method: 'POST' }),
  weeklyCloses: () => apiRequest<MarketWeeklyCloses>('/api/market-data/weekly-closes'),
  schedule: () => apiRequest<MarketDataSchedule>('/api/market-data/schedule'),
  updateSchedule: (schedule: Pick<MarketDataSchedule, 'enabled' | 'dayOfWeek' | 'runTime'>) =>
    apiRequest<MarketDataSchedule>('/api/market-data/schedule', {
      method: 'PUT',
      body: JSON.stringify(schedule),
    }),
  updateSector: (ticker: string, sector: string) => apiRequest<{ ticker: string; sector: string }>(
    `/api/market-data/${encodeURIComponent(ticker)}/sector`,
    { method: 'PUT', body: JSON.stringify({ sector }) },
  ),
}
