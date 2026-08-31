import {
  AlertCircle,
  ArrowRight,
  Clock3,
  Download,
  FileSpreadsheet,
  Filter,
  History,
  LoaderCircle,
  Pencil,
  Plus,
  RotateCcw,
  Trash2,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { OperationFormDialog } from '../components/OperationFormDialog'
import { portfolioApi } from '../portfolio/api'
import type {
  OperationAuditEntry,
  OperationBatch,
  OperationFilters,
  OperationsResponse,
  PortfolioOperation,
} from '../portfolio/api'
import { formatAmount, formatDate, formatDateTime, formatQuantity } from '../utils/formatters'

type Confirmation =
  | { kind: 'operation'; id: number; label: string }
  | { kind: 'batch'; id: number; label: string; count: number }

const EMPTY_FILTERS: OperationFilters = { from: '', to: '', ticker: '', type: '', sourceType: '', importBatchId: '' }

/** Administra operaciones manuales, importaciones y actividad reciente. */
export function OperationsPage() {
  const { t } = useTranslation()
  const [data, setData] = useState<OperationsResponse | null>(null)
  const [batches, setBatches] = useState<OperationBatch[]>([])
  const [audit, setAudit] = useState<OperationAuditEntry[]>([])
  const [draftFilters, setDraftFilters] = useState<OperationFilters>(EMPTY_FILTERS)
  const [filters, setFilters] = useState<OperationFilters>(EMPTY_FILTERS)
  const [editing, setEditing] = useState<PortfolioOperation | 'new' | null>(null)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setError(null)
    Promise.all([
      portfolioApi.operations(filters),
      portfolioApi.operationBatches(),
      portfolioApi.operationAudit(),
    ])
      .then(([operations, importedBatches, recentAudit]) => {
        if (!active) return
        setData(operations)
        setBatches(importedBatches)
        setAudit(recentAudit)
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : t('operations.loadError'))
      })
    return () => { active = false }
  }, [filters, reload, t])

  const refresh = () => setReload((value) => value + 1)

  /** Ejecuta la eliminación únicamente después de la confirmación visible. */
  const confirmDestructiveAction = async () => {
    if (!confirmation) return
    setBusy(true)
    setError(null)
    try {
      if (confirmation.kind === 'operation') {
        await portfolioApi.deleteOperation(confirmation.id)
      } else {
        await portfolioApi.revertOperationBatch(confirmation.id)
      }
      setConfirmation(null)
      refresh()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('operations.actionError'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="operations-page page-enter">
      <header className="page-heading operations-heading">
        <div>
          <span className="eyebrow">{t('operations.eyebrow')}</span>
          <h1>{t('operations.title')}</h1>
          <p>{t('operations.subtitle')}</p>
        </div>
        <div className="operations-heading__actions">
          <a className="quiet-button" href={portfolioApi.operationExportUrl(filters)} download>
            <Download size={16} /> {t('operations.export')}
          </a>
          <Link className="quiet-button" to="/importar"><FileSpreadsheet size={16} /> {t('operations.import')}</Link>
          <button className="secondary-button" type="button" onClick={() => setEditing('new')}>
            <Plus size={17} /> {t('operations.add')}
          </button>
        </div>
      </header>

      <OperationFiltersForm
        batches={batches}
        filters={draftFilters}
        onChange={setDraftFilters}
        onApply={() => setFilters({ ...draftFilters })}
        onClear={() => { setDraftFilters(EMPTY_FILTERS); setFilters(EMPTY_FILTERS) }}
      />

      {!data && !error && <div className="page-state"><LoaderCircle className="spin" size={25} /><span>{t('operations.loading')}</span></div>}
      {error && <div className="inline-alert inline-alert--error"><AlertCircle size={19} /><strong>{error}</strong></div>}

      {data && data.operations.length === 0 && <OperationsEmpty onAdd={() => setEditing('new')} />}

      {data && data.operations.length > 0 && (
        <OperationsBook
          data={data}
          onEdit={setEditing}
          onDelete={(operation) => setConfirmation({
            kind: 'operation',
            id: operation.id,
            label: operation.ticker ?? t('operations.cash'),
          })}
        />
      )}

      <ImportBatchesSection
        batches={batches}
        onRevert={(batch) => setConfirmation({
          kind: 'batch',
          id: batch.id,
          label: batch.sourceFilename,
          count: batch.currentOperations,
        })}
      />
      <AuditSection entries={audit} />

      {editing && (
        <OperationFormDialog
          operation={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); refresh() }}
        />
      )}
      {confirmation && (
        <ConfirmationDialog
          confirmation={confirmation}
          busy={busy}
          onCancel={() => setConfirmation(null)}
          onConfirm={confirmDestructiveAction}
        />
      )}
    </div>
  )
}

/** Formulario de filtros que solo actualiza el servidor al confirmar. */
function OperationFiltersForm({
  batches,
  filters,
  onChange,
  onApply,
  onClear,
}: {
  batches: OperationBatch[]
  filters: OperationFilters
  onChange: (filters: OperationFilters) => void
  onApply: () => void
  onClear: () => void
}) {
  const { t } = useTranslation()
  return (
    <form className="operation-filters" onSubmit={(event) => { event.preventDefault(); onApply() }}>
      <label><span>{t('operations.from')}</span><input type="date" value={filters.from ?? ''} onChange={(event) => onChange({ ...filters, from: event.target.value })} /></label>
      <label><span>{t('operations.to')}</span><input type="date" value={filters.to ?? ''} onChange={(event) => onChange({ ...filters, to: event.target.value })} /></label>
      <label><span>{t('operations.ticker')}</span><input value={filters.ticker ?? ''} placeholder="ECOPETROL.CL" onChange={(event) => onChange({ ...filters, ticker: event.target.value })} /></label>
      <label><span>{t('operations.type')}</span><select value={filters.type ?? ''} onChange={(event) => onChange({ ...filters, type: event.target.value as OperationFilters['type'] })}><option value="">{t('operations.all')}</option><OperationTypeOptions /></select></label>
      <label><span>{t('operations.source')}</span><select value={filters.sourceType ?? ''} onChange={(event) => onChange({ ...filters, sourceType: event.target.value as OperationFilters['sourceType'], importBatchId: '' })}><option value="">{t('operations.all')}</option><option value="MANUAL">{t('operations.manual')}</option><option value="IMPORT">{t('operations.excel')}</option></select></label>
      <label><span>{t('operations.file')}</span><select disabled={filters.sourceType === 'MANUAL'} value={filters.importBatchId ?? ''} onChange={(event) => onChange({ ...filters, sourceType: event.target.value ? 'IMPORT' : filters.sourceType, importBatchId: event.target.value ? Number(event.target.value) : '' })}><option value="">{t('operations.all')}</option>{batches.map((batch) => <option key={batch.id} value={batch.id}>{batch.sourceFilename}</option>)}</select></label>
      <div className="operation-filters__actions"><button className="quiet-button" type="button" onClick={onClear}>{t('operations.clear')}</button><button className="secondary-button" type="submit"><Filter size={16} />{t('operations.apply')}</button></div>
    </form>
  )
}

/** Libro tabular de escritorio y tarjetas equivalentes para móvil. */
function OperationsBook({
  data,
  onEdit,
  onDelete,
}: {
  data: OperationsResponse
  onEdit: (operation: PortfolioOperation) => void
  onDelete: (operation: PortfolioOperation) => void
}) {
  const { t } = useTranslation()
  return (
    <section className="operations-book">
      <div className="operations-summary"><span>{t('operations.totalLabel')}</span><strong>{data.total}</strong><small>{t(data.total === 1 ? 'operations.operation' : 'operations.operations')}</small>{data.total > data.returned && <small>{t('operations.showing', { count: data.returned })}</small>}</div>
      <div className="operations-table-wrap scrollable-table">
        <table className="operations-table">
          <thead><tr><th>{t('operations.date')}</th><th>{t('operations.type')}</th><th>{t('operations.asset')}</th><th>{t('operations.quantity')}</th><th>{t('operations.total')}</th><th>{t('operations.source')}</th><th>{t('operations.status')}</th><th aria-label={t('operations.actions')} /></tr></thead>
          <tbody>{data.operations.map((operation) => <OperationRow key={operation.id} operation={operation} onEdit={onEdit} onDelete={onDelete} />)}</tbody>
        </table>
      </div>
      <div className="operation-cards">{data.operations.map((operation) => <OperationCard key={operation.id} operation={operation} onEdit={onEdit} onDelete={onDelete} />)}</div>
    </section>
  )
}

function OperationRow({ operation, onEdit, onDelete }: { operation: PortfolioOperation; onEdit: (operation: PortfolioOperation) => void; onDelete: (operation: PortfolioOperation) => void }) {
  const { t } = useTranslation()
  return (
    <tr className={operation.consistencyIssue ? 'operation-row--issue' : undefined}>
      <td>{formatDate(operation.date)}</td><td><OperationBadge type={operation.type} /></td><td><Asset operation={operation} /></td>
      <td className="numeric-cell">{operation.quantity == null ? '—' : formatQuantity(operation.quantity)}</td>
      <td className={`numeric-cell cash-impact${operation.cashImpact >= 0 ? ' cash-impact--positive' : ' cash-impact--negative'}`}>{operation.cashImpact >= 0 ? '+' : '−'} {formatAmount(Math.abs(operation.cashImpact))}</td>
      <td><SourceLabel operation={operation} /></td>
      <td>{operation.consistencyIssue ? <span className="operation-issue" title={operation.consistencyIssue}><AlertCircle size={13} />{t('operations.review')}</span> : <span className="operation-ok">{t('operations.consistent')}</span>}</td>
      <td><RowActions operation={operation} onEdit={onEdit} onDelete={onDelete} /></td>
    </tr>
  )
}

function OperationCard({ operation, onEdit, onDelete }: { operation: PortfolioOperation; onEdit: (operation: PortfolioOperation) => void; onDelete: (operation: PortfolioOperation) => void }) {
  const { t } = useTranslation()
  return (
    <article className={`operation-card${operation.consistencyIssue ? ' operation-card--issue' : ''}`}>
      <div className="operation-card__top"><OperationBadge type={operation.type} /><time>{formatDate(operation.date)}</time></div>
      <Asset operation={operation} />
      {operation.consistencyIssue && <p className="operation-card__issue"><AlertCircle size={14} />{operation.consistencyIssue}</p>}
      <div className="operation-card__numbers"><span><small>{t('operations.quantity')}</small><strong>{operation.quantity == null ? '—' : formatQuantity(operation.quantity)}</strong></span><span><small>{t('operations.total')}</small><strong className={operation.cashImpact >= 0 ? 'positive' : 'negative'}>{operation.cashImpact >= 0 ? '+' : '−'} {formatAmount(Math.abs(operation.cashImpact))}</strong></span></div>
      <div className="operation-card__footer"><SourceLabel operation={operation} /><RowActions operation={operation} onEdit={onEdit} onDelete={onDelete} /></div>
    </article>
  )
}

function RowActions({ operation, onEdit, onDelete }: { operation: PortfolioOperation; onEdit: (operation: PortfolioOperation) => void; onDelete: (operation: PortfolioOperation) => void }) {
  const { t } = useTranslation()
  return <div className="row-actions"><button type="button" aria-label={t('operations.edit')} title={t('operations.edit')} onClick={() => onEdit(operation)}><Pencil size={15} /></button><button className="row-action--danger" type="button" aria-label={t('operations.delete')} title={t('operations.delete')} onClick={() => onDelete(operation)}><Trash2 size={15} /></button></div>
}

function SourceLabel({ operation }: { operation: PortfolioOperation }) {
  const { t } = useTranslation()
  return <span className={`source-label source-label--${operation.sourceType.toLowerCase()}`} title={operation.sourceFilename}>{operation.sourceType === 'MANUAL' ? t('operations.manual') : operation.sourceFilename}</span>
}

function Asset({ operation }: { operation: PortfolioOperation }) {
  const { t } = useTranslation()
  return <div className="operation-asset"><strong>{operation.ticker ?? t('operations.cash')}</strong><small>{operation.name ?? operation.notes ?? t('operations.cashMovement')}</small></div>
}

function OperationBadge({ type }: { type: PortfolioOperation['type'] }) {
  return <span className={`operation-badge operation-badge--${type.replace('ó', 'o')}`}>{type}</span>
}

function OperationTypeOptions() {
  const { t } = useTranslation()
  return <>{(['compra', 'venta', 'dividendo', 'depósito', 'retiro'] as const).map((type) => <option key={type} value={type}>{t(`operations.types.${type}`)}</option>)}</>
}

/** Lista lotes Excel por separado para hacer explícito el alcance de una reversión. */
function ImportBatchesSection({ batches, onRevert }: { batches: OperationBatch[]; onRevert: (batch: OperationBatch) => void }) {
  const { t } = useTranslation()
  return (
    <section className="operation-admin-section">
      <header><div><span className="eyebrow">{t('operations.batchesEyebrow')}</span><h2>{t('operations.batchesTitle')}</h2><p>{t('operations.batchesBody')}</p></div><FileSpreadsheet size={23} /></header>
      {batches.length === 0 ? <p className="admin-empty">{t('operations.noBatches')}</p> : <div className="admin-table-wrap scrollable-table"><table className="admin-table"><thead><tr><th>{t('operations.file')}</th><th>{t('operations.importedAt')}</th><th>{t('operations.importedBy')}</th><th>{t('operations.rows')}</th><th /></tr></thead><tbody>{batches.map((batch) => <tr key={batch.id}><td><strong>{batch.sourceFilename}</strong></td><td>{formatDateTime(batch.importedAt)}</td><td>{batch.importedBy}</td><td>{batch.currentOperations} / {batch.importedRows}</td><td><button className="table-danger-action" type="button" onClick={() => onRevert(batch)}><RotateCcw size={14} />{t('operations.revert')}</button></td></tr>)}</tbody></table></div>}
    </section>
  )
}

/** Muestra las últimas acciones sin exponer los snapshots internos de auditoría. */
function AuditSection({ entries }: { entries: OperationAuditEntry[] }) {
  const { t } = useTranslation()
  return (
    <section className="operation-admin-section">
      <header><div><span className="eyebrow">{t('operations.auditEyebrow')}</span><h2>{t('operations.auditTitle')}</h2><p>{t('operations.auditBody')}</p></div><History size={23} /></header>
      {entries.length === 0 ? <p className="admin-empty">{t('operations.noAudit')}</p> : <div className="admin-table-wrap scrollable-table"><table className="admin-table"><thead><tr><th>{t('operations.date')}</th><th>{t('operations.user')}</th><th>{t('operations.activity')}</th></tr></thead><tbody>{entries.map((entry) => <tr key={entry.id}><td>{formatDateTime(entry.createdAt)}</td><td>{entry.username}</td><td>{entry.details}</td></tr>)}</tbody></table></div>}
    </section>
  )
}

function OperationsEmpty({ onAdd }: { onAdd: () => void }) {
  const { t } = useTranslation()
  return <div className="operations-empty"><span><Clock3 size={27} /></span><h2>{t('operations.emptyTitle')}</h2><p>{t('operations.emptyBody')}</p><div className="empty-actions"><button className="secondary-button" type="button" onClick={onAdd}>{t('operations.add')} <Plus size={16} /></button><Link className="quiet-button" to="/importar">{t('operations.emptyAction')} <ArrowRight size={16} /></Link></div></div>
}

/** Confirmación modal diferenciada para una fila o una importación completa. */
function ConfirmationDialog({ confirmation, busy, onCancel, onConfirm }: { confirmation: Confirmation; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  const { t } = useTranslation()
  const batch = confirmation.kind === 'batch'
  return createPortal(<div className="dialog-backdrop" role="presentation"><section className="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title"><span className="confirm-dialog__icon"><Trash2 size={22} /></span><h2 id="confirm-title">{t(batch ? 'operations.confirmBatchTitle' : 'operations.confirmDeleteTitle')}</h2><p>{t(batch ? 'operations.confirmBatchBody' : 'operations.confirmDeleteBody', confirmation)}</p><div className="dialog-actions"><button className="quiet-button" type="button" disabled={busy} onClick={onCancel}>{t('operations.cancel')}</button><button className="danger-button" type="button" disabled={busy} onClick={onConfirm}>{busy ? <LoaderCircle className="spin" size={16} /> : <Trash2 size={16} />}{t(batch ? 'operations.confirmRevert' : 'operations.confirmDelete')}</button></div></section></div>, document.body)
}
