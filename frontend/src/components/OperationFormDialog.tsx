import { Calculator, LoaderCircle, Save, X } from 'lucide-react'
import { useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { ApiRequestError } from '../auth/api'
import { isOperationValidationPayload, portfolioApi } from '../portfolio/api'
import type { OperationInput, OperationTypeCode, PortfolioOperation } from '../portfolio/api'

interface OperationFormDialogProps {
  operation: PortfolioOperation | null
  onClose: () => void
  onSaved: () => void
}

interface FormState {
  date: string
  type: OperationTypeCode
  ticker: string
  name: string
  quantity: string
  unitPrice: string
  commission: string
  totalAmount: string
  notes: string
}

/** Formulario modal compartido por la creación y edición manual. */
export function OperationFormDialog({ operation, onClose, onSaved }: OperationFormDialogProps) {
  const { t } = useTranslation()
  const [form, setForm] = useState<FormState>(() => initialForm(operation))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const cashOperation = form.type === 'depósito' || form.type === 'retiro'
  const canCalculate = !cashOperation && form.quantity !== '' && form.unitPrice !== ''

  const setField = (field: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  /** Calcula el total con la misma fórmula que valida el backend. */
  const calculateTotal = () => {
    const quantity = Number(form.quantity)
    const price = Number(form.unitPrice)
    const commission = Number(form.commission || 0)
    if (!Number.isFinite(quantity) || !Number.isFinite(price) || !Number.isFinite(commission)) return
    const gross = quantity * price
    const total = form.type === 'compra' ? gross + commission : gross - commission
    setField('totalAmount', Math.max(0, total).toFixed(2))
  }

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    try {
      const payload = formPayload(form)
      if (operation) {
        await portfolioApi.updateOperation(operation.id, payload)
      } else {
        await portfolioApi.createOperation(payload)
      }
      onSaved()
    } catch (requestError) {
      if (requestError instanceof ApiRequestError && isOperationValidationPayload(requestError.payload)) {
        setFieldErrors(Object.fromEntries(requestError.payload.errors.map((item) => [item.field, item.message])))
        setError(requestError.payload.message)
      } else {
        setError(requestError instanceof ApiRequestError ? requestError.message : t('operations.form.saveError'))
      }
    } finally {
      setSaving(false)
    }
  }

  return createPortal(
    <div className="dialog-backdrop" role="presentation">
      <section className="operation-dialog" role="dialog" aria-modal="true" aria-labelledby="operation-dialog-title">
        <header className="operation-dialog__header">
          <div><span className="eyebrow">{t(operation ? 'operations.form.editEyebrow' : 'operations.form.createEyebrow')}</span><h2 id="operation-dialog-title">{t(operation ? 'operations.form.editTitle' : 'operations.form.createTitle')}</h2><p>{t('operations.form.subtitle')}</p></div>
          <button type="button" aria-label={t('operations.form.close')} onClick={onClose}><X size={19} /></button>
        </header>
        <form className="operation-form" onSubmit={submit}>
          {error && <div className="inline-alert inline-alert--error"><span>{error}</span></div>}
          <div className="operation-form__grid">
            <FormField label={t('operations.form.date')} error={fieldErrors.date}><input type="date" max={todayInBogota()} value={form.date} onChange={(event) => setField('date', event.target.value)} /></FormField>
            <FormField label={t('operations.form.type')} error={fieldErrors.type}><select value={form.type} onChange={(event) => setField('type', event.target.value as OperationTypeCode)}>{(['compra', 'venta', 'dividendo', 'depósito', 'retiro'] as const).map((type) => <option key={type} value={type}>{t(`operations.types.${type}`)}</option>)}</select></FormField>
            {!cashOperation && <><FormField label={t('operations.form.ticker')} error={fieldErrors.ticker}><input value={form.ticker} placeholder="ECOPETROL.CL" maxLength={30} onChange={(event) => setField('ticker', event.target.value.toUpperCase())} /></FormField><FormField label={t('operations.form.name')} error={fieldErrors.name}><input value={form.name} maxLength={160} onChange={(event) => setField('name', event.target.value)} /></FormField></>}
            {!cashOperation && <><FormField label={t('operations.form.quantity')} error={fieldErrors.quantity}><input type="number" min="0" step="any" value={form.quantity} onChange={(event) => setField('quantity', event.target.value)} /></FormField><FormField label={t('operations.form.unitPrice')} error={fieldErrors.unitPrice}><input type="number" min="0" step="any" value={form.unitPrice} onChange={(event) => setField('unitPrice', event.target.value)} /></FormField><FormField label={t('operations.form.commission')} error={fieldErrors.commission}><input type="number" min="0" step="0.01" value={form.commission} onChange={(event) => setField('commission', event.target.value)} /></FormField></>}
            <FormField label={t('operations.form.totalAmount')} error={fieldErrors.totalAmount}><div className="amount-input"><input type="number" min="0" step="0.01" value={form.totalAmount} onChange={(event) => setField('totalAmount', event.target.value)} />{canCalculate && <button type="button" title={t('operations.form.calculate')} aria-label={t('operations.form.calculate')} onClick={calculateTotal}><Calculator size={16} /></button>}</div></FormField>
            <FormField className="operation-form__notes" label={t('operations.form.notes')} error={fieldErrors.notes}><textarea rows={3} maxLength={1000} value={form.notes} onChange={(event) => setField('notes', event.target.value)} /></FormField>
          </div>
          {operation && <p className="operation-form__origin">{t('operations.form.origin', { source: operation.sourceType === 'MANUAL' ? t('operations.manual') : operation.sourceFilename })}</p>}
          <div className="dialog-actions"><button className="quiet-button" type="button" disabled={saving} onClick={onClose}>{t('operations.cancel')}</button><button className="secondary-button" type="submit" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}{t(operation ? 'operations.form.saveChanges' : 'operations.form.create')}</button></div>
        </form>
      </section>
    </div>,
    document.body,
  )
}

/** Mantiene etiqueta y error unidos para lectores de pantalla y diseño consistente. */
function FormField({ label, error, className = '', children }: { label: string; error?: string; className?: string; children: React.ReactNode }) {
  return <label className={`operation-form__field ${error ? 'operation-form__field--error' : ''} ${className}`}><span>{label}</span>{children}{error && <small>{error}</small>}</label>
}

function initialForm(operation: PortfolioOperation | null): FormState {
  return {
    date: operation?.date ?? todayInBogota(),
    type: operation?.type ?? 'compra',
    ticker: operation?.ticker ?? '',
    name: operation?.name ?? '',
    quantity: operation?.quantity == null ? '' : String(operation.quantity),
    unitPrice: operation?.unitPrice == null ? '' : String(operation.unitPrice),
    commission: operation == null ? '0' : String(operation.commission),
    totalAmount: operation == null ? '' : String(operation.totalAmount),
    notes: operation?.notes ?? '',
  }
}

function formPayload(form: FormState): OperationInput {
  const cashOperation = form.type === 'depósito' || form.type === 'retiro'
  return {
    date: form.date,
    type: form.type,
    ticker: cashOperation ? null : blankToNull(form.ticker),
    name: cashOperation ? null : blankToNull(form.name),
    quantity: cashOperation ? null : numberOrNull(form.quantity),
    unitPrice: cashOperation ? null : numberOrNull(form.unitPrice),
    commission: cashOperation ? 0 : numberOrZero(form.commission),
    totalAmount: numberOrZero(form.totalAmount),
    notes: blankToNull(form.notes),
  }
}

function numberOrNull(value: string) {
  if (value.trim() === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function numberOrZero(value: string) {
  return numberOrNull(value) ?? 0
}

function blankToNull(value: string) {
  return value.trim() === '' ? null : value.trim()
}

function todayInBogota() {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'America/Bogota',
  }).format(new Date())
}
