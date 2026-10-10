import { LoaderCircle, Save, Sparkles, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiRequestError } from '../auth/api'
import type { PortfolioDefinition } from '../portfolio/PortfolioProvider'
import { reportApi } from './api'
import type { PortfolioReportAiGuidance } from './api'

export function AiGuidancePanel({ portfolios }: { portfolios: PortfolioDefinition[] }) {
  const { t } = useTranslation()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const portfolioId = selectedId ?? portfolios[0]?.id
  return (
    <div className="market-schedule-card ai-guidance-card">
      <div className="market-schedule-card__intro"><span><Sparkles size={20} /></span><div><h3>{t('settings.guidanceTitle')}</h3><p>{t('settings.guidanceBody')}</p></div></div>
      <label><span>{t('settings.guidancePortfolio')}</span><select value={portfolioId ?? ''} onChange={(event) => setSelectedId(Number(event.target.value))}>{portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>)}</select></label>
      {portfolioId && <GuidanceForm key={portfolioId} portfolioId={portfolioId} />}
    </div>
  )
}

function GuidanceForm({ portfolioId }: { portfolioId: number }) {
  const { t } = useTranslation()
  const [guidance, setGuidance] = useState<PortfolioReportAiGuidance | null>(null)
  const [text, setText] = useState('')
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void reportApi.aiGuidance(portfolioId).then((saved) => {
      if (active) { setGuidance(saved); setText(saved.text) }
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : t('settings.guidanceLoadError'))
    })
    return () => { active = false }
  }, [portfolioId, t])

  async function update(cancel: boolean) {
    setWorking(true)
    setError(null)
    setMessage(null)
    try {
      const saved = cancel ? await reportApi.cancelAiGuidance(portfolioId) : await reportApi.saveAiGuidance(portfolioId, text)
      setGuidance(saved)
      setText(saved.text)
      setMessage(t(cancel ? 'settings.guidanceCancelled' : 'settings.guidanceSaved'))
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('settings.guidanceSaveError'))
    } finally { setWorking(false) }
  }

  return (
    <form className="ai-guidance-form" aria-label={t('settings.guidanceTitle')} onSubmit={(event) => { event.preventDefault(); void update(false) }}>
      <label><span>{t('settings.guidanceText')}</span><textarea rows={4} maxLength={2000} value={text} onChange={(event) => setText(event.target.value)} placeholder={t('settings.guidancePlaceholder')} required disabled={!guidance || working} /></label>
      <p role="status">{guidance ? t(`settings.guidanceStatus.${guidance.status}`) : t('settings.loading')}</p>
      <p>{t('settings.guidanceHelp')}</p>
      <div className="whatsapp-recipient-actions">
        <button className="secondary-button" type="submit" disabled={!guidance || working || !text.trim()}>{working ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}{t('settings.guidanceSave')}</button>
        <button className="quiet-button" type="button" disabled={working || guidance?.status !== 'PENDING'} onClick={() => void update(true)}><Trash2 size={16} />{t('settings.guidanceCancel')}</button>
      </div>
      {message && <strong className="settings-feedback" role="status">{message}</strong>}
      {error && <strong className="settings-feedback" role="alert">{error}</strong>}
    </form>
  )
}
