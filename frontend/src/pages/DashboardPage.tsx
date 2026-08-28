import { ArrowUpRight, CircleDollarSign, Landmark, PiggyBank, Sparkles, WalletCards } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { portfolioApi } from '../portfolio/api'
import { formatCop, formatPercentage } from '../utils/formatters'

const metrics = [
  { key: 'portfolioValue', value: formatCop(0), icon: WalletCards, tone: 'violet' },
  { key: 'investedCapital', value: formatCop(0), icon: Landmark, tone: 'blue' },
  { key: 'totalReturn', value: formatPercentage(0), icon: ArrowUpRight, tone: 'green' },
  { key: 'availableCash', value: formatCop(0), icon: CircleDollarSign, tone: 'coral' },
]

/** Presenta el resumen inicial y el estado de preparación del portafolio. */
export function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [operationCount, setOperationCount] = useState(0)

  useEffect(() => {
    let active = true
    portfolioApi.operations()
      .then((response) => { if (active) setOperationCount(response.total) })
      .catch(() => undefined)
    return () => { active = false }
  }, [])

  const hasOperations = operationCount > 0

  return (
    <div className="dashboard page-enter">
      <header className="page-heading">
        <div>
          <span className="eyebrow">Panel principal</span>
          <h1>{t('dashboard.greeting', { name: user?.username })}</h1>
          <p>{t('dashboard.subtitle')}</p>
        </div>
        <div className="system-pill"><span /> {t('dashboard.status')}</div>
      </header>

      <section className="metric-grid" aria-label="Resumen del portafolio">
        {metrics.map(({ key, value, icon: Icon, tone }) => (
          <article className="metric-card" key={key}>
            <span className={`metric-card__icon metric-card__icon--${tone}`}><Icon size={20} /></span>
            <span className="metric-card__label">{t(`dashboard.${key}`)}</span>
            <strong>{value}</strong>
            <small>{hasOperations ? t('dashboard.calculationPending') : t('dashboard.noOperations')}</small>
          </article>
        ))}
      </section>

      <section className="dashboard-grid">
        <article className="chart-card">
          <div className="card-heading">
            <div>
              <h2>{t('dashboard.chartTitle')}</h2>
              <p>{t('dashboard.chartSubtitle')}</p>
            </div>
            <span className="period-chip">Todo</span>
          </div>
          <div className="empty-chart">
            <div className="empty-chart__grid" />
            <div className="empty-chart__content">
              <span className="empty-chart__icon"><PiggyBank size={25} /></span>
              <h3>{t(hasOperations ? 'dashboard.readyTitle' : 'dashboard.emptyTitle')}</h3>
              <p>{t(hasOperations ? 'dashboard.readyBody' : 'dashboard.emptyBody', { count: operationCount })}</p>
              <Link className="secondary-button" to={hasOperations ? '/mercado' : '/importar'}>
                {t(hasOperations ? 'dashboard.readyAction' : 'dashboard.emptyAction')}
              </Link>
            </div>
          </div>
        </article>

        <aside className="foundation-card">
          <span className="foundation-card__icon"><Sparkles size={23} /></span>
          <span className="eyebrow">Incremento 3</span>
          <h2>{t('dashboard.status')}</h2>
          <p>{t('dashboard.statusDetail')}.</p>
          <ul>
            <li><span /> Yahoo Finance</li>
            <li><span /> Cierres diarios OHLCV</li>
            <li><span /> Precios provisionales</li>
            <li><span /> Entorno Docker</li>
          </ul>
        </aside>
      </section>
    </div>
  )
}
