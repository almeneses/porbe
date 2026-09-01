import {
  AlertCircle,
  ArrowRight,
  CalendarDays,
  ChartNoAxesCombined,
  CheckCircle2,
  Clock3,
  CloudDownload,
  Database,
  LoaderCircle,
  ImageUp,
  RefreshCw,
  Save,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { marketDataApi } from '../market/api'
import type { MarketDataSchedule, MarketDataStatus, MarketDataSyncResult, MarketTickerStatus, WeekDay } from '../market/api'
import { formatCurrency, formatDate, formatDateTime } from '../utils/formatters'
import { usePortfolio } from '../portfolio/PortfolioProvider'
import { TickerIcon } from '../components/TickerIcon'

/** Muestra la cobertura de mercado y permite actualizar precios desde Yahoo. */
export function MarketDataPage() {
  const { t } = useTranslation()
  const { activePortfolio } = usePortfolio()
  const [status, setStatus] = useState<MarketDataStatus | null>(null)
  const [syncResult, setSyncResult] = useState<MarketDataSyncResult | null>(null)
  const [schedule, setSchedule] = useState<MarketDataSchedule | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [syncing, setSyncing] = useState(false)

  const loadStatus = useCallback(async () => {
    try {
      setStatus(await marketDataApi.status(activePortfolio.id))
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.loadError'))
    }
  }, [activePortfolio.id, t])

  useEffect(() => {
    void loadStatus()
    marketDataApi.schedule()
      .then(setSchedule)
      .catch((requestError) => setError(requestError instanceof ApiRequestError ? requestError.message : t('market.scheduleLoadError')))
  }, [loadStatus, t])

  // Recarga el estado después de sincronizar para reflejar lo persistido.
  async function syncPrices() {
    setSyncing(true)
    setError(null)
    setSyncResult(null)
    try {
      const result = await marketDataApi.sync(activePortfolio.id)
      setSyncResult(result)
      await loadStatus()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.syncError'))
    } finally {
      setSyncing(false)
    }
  }

  const hasTickers = Boolean(status?.tickers.length)

  return (
    <div className="market-page page-enter">
      <header className="page-heading market-heading">
        <div>
          <span className="eyebrow">{t('market.eyebrow')}</span>
          <h1>{t('market.title')}</h1>
          <p>{t('market.subtitle')}</p>
        </div>
        <button className="secondary-button market-sync" type="button" onClick={syncPrices} disabled={!hasTickers || syncing}>
          {syncing ? <LoaderCircle className="spin" size={17} /> : <RefreshCw size={17} />}
          {t(syncing ? 'market.syncing' : 'market.sync')}
        </button>
      </header>

      {!status && !error && (
        <div className="page-state"><LoaderCircle className="spin" size={25} /><span>{t('market.loading')}</span></div>
      )}

      {error && (
        <div className="page-state page-state--error"><AlertCircle size={28} /><strong>{error}</strong></div>
      )}

      {status && !hasTickers && (
        <div className="market-empty">
          <span><ChartNoAxesCombined size={27} /></span>
          <h2>{t('market.emptyTitle')}</h2>
          <p>{t('market.emptyBody')}</p>
          <Link className="secondary-button" to="/importar">{t('market.emptyAction')} <ArrowRight size={16} /></Link>
        </div>
      )}

      {status && hasTickers && (
        <>
          <section className="market-source-bar" aria-label={t('market.sourceLabel')}>
            <div><CloudDownload size={18} /><span>{t('market.sourceLabel')}</span><strong>Yahoo Finance</strong></div>
            <div><Database size={18} /><span>{t('market.coverage')}</span><strong>{status.tickers.length} {status.tickers.length === 1 ? t('market.asset') : t('market.assets')}</strong></div>
          </section>

          {syncResult && <SyncResult result={syncResult} />}

          {schedule && <SchedulePanel schedule={schedule} onSaved={setSchedule} />}

          <section className="market-grid" aria-label={t('market.assetsLabel')}>
            {status.tickers.map((ticker) => <TickerCard key={ticker.ticker} ticker={ticker} onSectorSaved={loadStatus} />)}
          </section>
        </>
      )}
    </div>
  )
}

/** Resume el resultado agregado e individual de una sincronización. */
function SyncResult({ result }: { result: MarketDataSyncResult }) {
  const { t } = useTranslation()
  const completed = result.totalTickers > 0 && result.successfulTickers === result.totalTickers
  return (
    <section className={`market-result market-result--${completed ? 'success' : 'warning'}`}>
      <div>
        {completed ? <CheckCircle2 size={20} /> : <AlertCircle size={20} />}
        <span><strong>{result.message}</strong><small>{t('market.pointsStored', { count: result.storedPrices })}</small></span>
      </div>
      <ul>
        {result.results.map((item) => (
          <li key={item.ticker}>
            <strong>{item.ticker}</strong>
            <span>{item.message}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Presenta el último precio y la cobertura almacenada de un activo. */
function TickerCard({ ticker, onSectorSaved }: { ticker: MarketTickerStatus; onSectorSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const hasPrice = ticker.lastPriceDate !== null && ticker.lastClose !== null
  return (
    <article className="market-card">
      <div className="market-card__top">
        <div className="market-card__identity"><TickerIcon key={ticker.iconUpdatedAt ?? ticker.ticker} ticker={ticker.ticker} size={46} /><span className="market-card__ticker">{ticker.ticker}</span></div>
        <span className={`market-status market-status--${hasPrice ? (ticker.provisional ? 'provisional' : 'final') : 'pending'}`}>
          {t(hasPrice ? (ticker.provisional ? 'market.provisional' : 'market.finalClose') : 'market.pending')}
        </span>
      </div>
      <h2>{ticker.name ?? t('market.namePending')}</h2>
      <p>{[ticker.exchange, ticker.currency].filter(Boolean).join(' · ') || 'Yahoo Finance'}</p>
      <div className="market-card__price">
        <span>{t('market.lastPrice')}</span>
        <strong>{hasPrice ? formatCurrency(ticker.lastClose!, ticker.currency ?? 'COP') : '—'}</strong>
      </div>
      <dl>
        <div><dt><CalendarDays size={15} /> {t('market.priceDate')}</dt><dd>{ticker.lastPriceDate ? formatDate(ticker.lastPriceDate) : '—'}</dd></div>
        <div><dt><Database size={15} /> {t('market.storedDays')}</dt><dd>{ticker.storedDays}</dd></div>
      </dl>
      <SectorEditor ticker={ticker} onSaved={onSectorSaved} />
      <IconEditor ticker={ticker} onSaved={onSectorSaved} />
      <small className="market-card__updated">
        {ticker.lastSyncedAt ? t('market.updatedAt', { date: formatDateTime(ticker.lastSyncedAt) }) : t('market.neverUpdated')}
      </small>
    </article>
  )
}

/** Carga, reemplaza o retira el ícono que se reutiliza en toda la aplicación y en los informes. */
function IconEditor({ ticker, onSaved }: { ticker: MarketTickerStatus; onSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function upload(file?: File) {
    if (!file) return
    setSaving(true)
    setError(null)
    try {
      await marketDataApi.updateIcon(ticker.ticker, file)
      await onSaved()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.iconSaveError'))
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    setSaving(true)
    setError(null)
    try {
      await marketDataApi.removeIcon(ticker.ticker)
      await onSaved()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.iconRemoveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="market-icon-editor">
      <span>{t('market.tickerIcon')}</span>
      <div>
        <label className="quiet-button"><ImageUp size={14} />{t(ticker.hasIcon ? 'market.replaceIcon' : 'market.addIcon')}<input type="file" accept="image/png,image/jpeg" disabled={saving} onChange={(event) => { void upload(event.target.files?.[0]); event.target.value = '' }} /></label>
        {ticker.hasIcon && <button className="icon-button" type="button" title={t('market.removeIcon')} aria-label={t('market.removeIcon')} disabled={saving} onClick={remove}>{saving ? <LoaderCircle className="spin" size={14} /> : <Trash2 size={14} />}</button>}
      </div>
      <small>{t('market.iconHint')}</small>
      {error && <small role="alert" className="field-error">{error}</small>}
    </div>
  )
}

/** Permite activar y modificar el día y hora del trabajo semanal. */
function SchedulePanel({ schedule, onSaved }: { schedule: MarketDataSchedule; onSaved: (schedule: MarketDataSchedule) => void }) {
  const { t } = useTranslation()
  const [enabled, setEnabled] = useState(schedule.enabled)
  const [dayOfWeek, setDayOfWeek] = useState<WeekDay>(schedule.dayOfWeek)
  const [runTime, setRunTime] = useState(schedule.runTime.slice(0, 5))
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function saveSchedule(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setMessage(null)
    try {
      const updated = await marketDataApi.updateSchedule({ enabled, dayOfWeek, runTime })
      onSaved(updated)
      setMessage(t('market.scheduleSaved'))
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('market.scheduleSaveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="market-schedule-card">
      <div className="market-schedule-card__intro"><span><Clock3 size={20} /></span><div><h2>{t('market.scheduleTitle')}</h2><p>{t('market.scheduleBody')}</p></div></div>
      <form onSubmit={saveSchedule}>
        <label className="schedule-switch"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /><span>{t(enabled ? 'market.scheduleEnabled' : 'market.scheduleDisabled')}</span></label>
        <label><span>{t('market.scheduleDay')}</span><select value={dayOfWeek} onChange={(event) => setDayOfWeek(event.target.value as WeekDay)}>{(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as WeekDay[]).map((day) => <option key={day} value={day}>{t(`market.days.${day}`)}</option>)}</select></label>
        <label><span>{t('market.scheduleTime')}</span><input type="time" value={runTime} onChange={(event) => setRunTime(event.target.value)} required /></label>
        <button className="secondary-button" type="submit" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}{t('market.saveSchedule')}</button>
      </form>
      <div className="market-schedule-card__status">
        <span>{t('market.scheduleTimezone', { timezone: schedule.timezone })}</span>
        <span>{schedule.nextRunAt ? t('market.nextRun', { date: formatDateTime(schedule.nextRunAt) }) : t('market.noNextRun')}</span>
        {schedule.lastRunAt && <span>{t('market.lastRun', { date: formatDateTime(schedule.lastRunAt), status: t(`market.runStatus.${schedule.lastRunStatus}`) })}</span>}
        {message && <strong>{message}</strong>}
      </div>
    </section>
  )
}

/** Guarda una clasificación sectorial reutilizada por el dashboard. */
function SectorEditor({ ticker, onSaved }: { ticker: MarketTickerStatus; onSaved: () => Promise<void> }) {
  const { t } = useTranslation()
  const [sector, setSector] = useState(ticker.sector)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function saveSector() {
    if (!sector.trim() || sector.trim() === ticker.sector) return
    setSaving(true)
    setError(null)
    try {
      await marketDataApi.updateSector(ticker.ticker, sector.trim())
      await onSaved()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.sectorSaveError'))
    } finally {
      setSaving(false)
    }
  }

  return <label className="market-sector"><span>{t('market.sector')}</span><div><input value={sector} maxLength={80} onChange={(event) => setSector(event.target.value)} /><button type="button" aria-label={t('market.saveSector')} title={t('market.saveSector')} disabled={saving || !sector.trim() || sector.trim() === ticker.sector} onClick={saveSector}>{saving ? <LoaderCircle className="spin" size={14} /> : <Save size={14} />}</button></div>{error && <small role="alert">{error}</small>}</label>
}
