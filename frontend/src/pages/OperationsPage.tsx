import { AlertCircle, ArrowRight, Clock3, FileSpreadsheet, LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { portfolioApi } from '../portfolio/api'
import type { OperationsResponse, PortfolioOperation } from '../portfolio/api'
import { formatAmount, formatDate, formatQuantity } from '../utils/formatters'

/** Lista las operaciones importadas con vistas adaptadas a PC y móvil. */
export function OperationsPage() {
  const { t } = useTranslation()
  const [data, setData] = useState<OperationsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    portfolioApi.operations()
      .then((response) => { if (active) setData(response) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : t('operations.loadError'))
      })
    return () => { active = false }
  }, [t])

  return (
    <div className="operations-page page-enter">
      <header className="page-heading operations-heading">
        <div>
          <span className="eyebrow">{t('operations.eyebrow')}</span>
          <h1>{t('operations.title')}</h1>
          <p>{t('operations.subtitle')}</p>
        </div>
        <Link className="secondary-button" to="/importar"><FileSpreadsheet size={17} /> {t('operations.import')}</Link>
      </header>

      {!data && !error && <div className="page-state"><LoaderCircle className="spin" size={25} /><span>{t('operations.loading')}</span></div>}
      {error && <div className="page-state page-state--error"><AlertCircle size={24} /><strong>{error}</strong></div>}

      {data && data.operations.length === 0 && (
        <div className="operations-empty">
          <span><Clock3 size={27} /></span>
          <h2>{t('operations.emptyTitle')}</h2>
          <p>{t('operations.emptyBody')}</p>
          <Link className="secondary-button" to="/importar">{t('operations.emptyAction')} <ArrowRight size={16} /></Link>
        </div>
      )}

      {data && data.operations.length > 0 && (
        <>
          <div className="operations-summary">
            <span>{t('operations.totalLabel')}</span>
            <strong>{data.total}</strong>
            <small>{data.total === 1 ? t('operations.operation') : t('operations.operations')}</small>
          </div>
          <div className="operations-table-wrap">
            <table className="operations-table">
              <thead>
                <tr>
                  <th>{t('operations.date')}</th>
                  <th>{t('operations.type')}</th>
                  <th>{t('operations.asset')}</th>
                  <th>{t('operations.quantity')}</th>
                  <th>{t('operations.unitPrice')}</th>
                  <th>{t('operations.commission')}</th>
                  <th>{t('operations.total')}</th>
                </tr>
              </thead>
              <tbody>
                {data.operations.map((operation) => <OperationRow key={operation.id} operation={operation} />)}
              </tbody>
            </table>
          </div>
          <div className="operation-cards">
            {data.operations.map((operation) => <OperationCard key={operation.id} operation={operation} />)}
          </div>
        </>
      )}
    </div>
  )
}

/** Fila tabular usada en pantallas amplias. */
function OperationRow({ operation }: { operation: PortfolioOperation }) {
  return (
    <tr>
      <td>{formatDate(operation.date)}</td>
      <td><OperationBadge type={operation.type} /></td>
      <td><Asset operation={operation} /></td>
      <td className="numeric-cell">{operation.quantity == null ? '—' : formatQuantity(operation.quantity)}</td>
      <td className="numeric-cell">{operation.unitPrice == null ? '—' : formatAmount(operation.unitPrice)}</td>
      <td className="numeric-cell">{formatAmount(operation.commission)}</td>
      <td className={`numeric-cell cash-impact${operation.cashImpact >= 0 ? ' cash-impact--positive' : ' cash-impact--negative'}`}>
        {operation.cashImpact >= 0 ? '+' : '−'} {formatAmount(Math.abs(operation.cashImpact))}
      </td>
    </tr>
  )
}

/** Tarjeta compacta de una operación para pantallas pequeñas. */
function OperationCard({ operation }: { operation: PortfolioOperation }) {
  return (
    <article className="operation-card">
      <div className="operation-card__top">
        <OperationBadge type={operation.type} />
        <time>{formatDate(operation.date)}</time>
      </div>
      <Asset operation={operation} />
      <div className="operation-card__numbers">
        <span><small>Cantidad</small><strong>{operation.quantity == null ? '—' : formatQuantity(operation.quantity)}</strong></span>
        <span><small>Total</small><strong className={operation.cashImpact >= 0 ? 'positive' : 'negative'}>{operation.cashImpact >= 0 ? '+' : '−'} {formatAmount(Math.abs(operation.cashImpact))}</strong></span>
      </div>
    </article>
  )
}

function Asset({ operation }: { operation: PortfolioOperation }) {
  return (
    <div className="operation-asset">
      <strong>{operation.ticker ?? 'Efectivo'}</strong>
      <small>{operation.name ?? operation.notes ?? 'Movimiento de efectivo'}</small>
    </div>
  )
}

function OperationBadge({ type }: { type: PortfolioOperation['type'] }) {
  return <span className={`operation-badge operation-badge--${type.replace('ó', 'o')}`}>{type}</span>
}
