import { BriefcaseBusiness, LoaderCircle, Pencil, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { createPortal } from 'react-dom'
import { ApiRequestError } from '../auth/api'
import { usePortfolio } from '../portfolio/PortfolioProvider'
import { useTranslation } from 'react-i18next'

/** Permite cambiar, crear y renombrar portafolios desde cualquier pantalla. */
export function PortfolioSwitcher({ compact = false }: { compact?: boolean }) {
  const { t } = useTranslation()
  const { portfolios, activePortfolio, selectPortfolio, createPortfolio, renamePortfolio } = usePortfolio()
  const [dialog, setDialog] = useState<'create' | 'rename' | null>(null)
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function open(kind: 'create' | 'rename') {
    setDialog(kind)
    setName(kind === 'rename' ? activePortfolio.name : '')
    setError(null)
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      if (dialog === 'create') await createPortfolio(name)
      else await renamePortfolio(activePortfolio.id, name)
      setDialog(null)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('portfolio.saveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={`portfolio-switcher${compact ? ' portfolio-switcher--compact' : ''}`}>
      {!compact && <span className="portfolio-switcher__label">{t('portfolio.active')}</span>}
      <div className="portfolio-switcher__control">
        <BriefcaseBusiness size={17} />
        <select aria-label={t('portfolio.active')} value={activePortfolio.id} onChange={(event) => selectPortfolio(Number(event.target.value))}>
          {portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>)}
        </select>
        <button type="button" title={t('portfolio.rename')} aria-label={t('portfolio.rename')} onClick={() => open('rename')}><Pencil size={14} /></button>
        <button type="button" title={t('portfolio.create')} aria-label={t('portfolio.create')} onClick={() => open('create')}><Plus size={15} /></button>
      </div>
      {dialog && createPortal(
        <div className="dialog-backdrop" role="presentation">
          <section className="portfolio-dialog" role="dialog" aria-modal="true" aria-labelledby="portfolio-dialog-title">
            <header><div><span className="eyebrow">{t('portfolio.organization')}</span><h2 id="portfolio-dialog-title">{t(dialog === 'create' ? 'portfolio.createTitle' : 'portfolio.renameTitle')}</h2><p>{t('portfolio.dialogBody')}</p></div><button type="button" aria-label={t('portfolio.close')} onClick={() => setDialog(null)}><X size={18} /></button></header>
            <form onSubmit={save}>
              <label><span>{t('portfolio.name')}</span><input autoFocus value={name} maxLength={120} onChange={(event) => setName(event.target.value)} required /></label>
              {error && <div className="inline-alert inline-alert--error">{error}</div>}
              <div className="dialog-actions"><button className="quiet-button" type="button" onClick={() => setDialog(null)} disabled={saving}>{t('portfolio.cancel')}</button><button className="secondary-button" type="submit" disabled={saving || !name.trim()}>{saving ? <LoaderCircle className="spin" size={16} /> : dialog === 'create' ? <Plus size={16} /> : <Pencil size={16} />}{t(dialog === 'create' ? 'portfolio.createAction' : 'portfolio.saveName')}</button></div>
            </form>
          </section>
        </div>,
        document.body,
      )}
    </div>
  )
}
