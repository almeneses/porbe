import {
  AlertCircle,
  ArrowRight,
  CalendarDays,
  ChartNoAxesCombined,
  CheckCircle2,
  CloudDownload,
  Database,
  LoaderCircle,
  RefreshCw,
} from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { marketDataApi } from '../market/api'
import type { MarketDataStatus, MarketDataSyncResult, MarketTickerStatus } from '../market/api'
import { formatCurrency, formatDate, formatDateTime } from '../utils/formatters'

/** Muestra la cobertura de mercado y permite actualizar precios desde Yahoo. */
export function MarketDataPage() {
  const { t } = useTranslation()
  const [status, setStatus] = useState<MarketDataStatus | null>(null)
  const [syncResult, setSyncResult] = useState<MarketDataSyncResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [syncing, setSyncing] = useState(false)

  const loadStatus = useCallback(async () => {
    try {
      setStatus(await marketDataApi.status())
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('market.loadError'))
    }
  }, [t])

  useEffect(() => {
    void loadStatus()
  }, [loadStatus])

  // Recarga el estado después de sincronizar para reflejar lo persistido.
  async function syncPrices() {
    setSyncing(true)
    setError(null)
    setSyncResult(null)
    try {
      const result = await marketDataApi.sync()
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

          <section className="market-grid" aria-label={t('market.assetsLabel')}>
            {status.tickers.map((ticker) => <TickerCard key={ticker.ticker} ticker={ticker} />)}
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
function TickerCard({ ticker }: { ticker: MarketTickerStatus }) {
  const { t } = useTranslation()
  const hasPrice = ticker.lastPriceDate !== null && ticker.lastClose !== null
  return (
    <article className="market-card">
      <div className="market-card__top">
        <span className="market-card__ticker">{ticker.ticker}</span>
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
      <small className="market-card__updated">
        {ticker.lastSyncedAt ? t('market.updatedAt', { date: formatDateTime(ticker.lastSyncedAt) }) : t('market.neverUpdated')}
      </small>
    </article>
  )
}
