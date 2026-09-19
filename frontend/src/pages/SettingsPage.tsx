import { AlertCircle, BriefcaseBusiness, Clock3, FileText, LoaderCircle, MessageCircleMore, RefreshCw, Save, Send, Sparkles, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { marketDataApi } from '../market/api'
import type { MarketDataSchedule, WeekDay } from '../market/api'
import { usePortfolio } from '../portfolio/PortfolioProvider'
import type { PortfolioDefinition } from '../portfolio/PortfolioProvider'
import { reportApi } from '../report/api'
import type { PortfolioReportAiSettings, PortfolioReportSchedule, WhatsAppConnectionStatus, WhatsAppRecipient } from '../report/api'
import { formatDateTime } from '../utils/formatters'

/** Reúne las preferencias operativas que pueden modificarse desde la aplicación. */
export function SettingsPage() {
  const { t } = useTranslation()
  const { portfolios, updateScheduledReport } = usePortfolio()
  const [marketSchedule, setMarketSchedule] = useState<MarketDataSchedule | null>(null)
  const [reportSchedule, setReportSchedule] = useState<PortfolioReportSchedule | null>(null)
  const [aiSettings, setAiSettings] = useState<PortfolioReportAiSettings | null>(null)
  const [recipients, setRecipients] = useState<WhatsAppRecipient[] | null>(null)
  const [whatsAppStatus, setWhatsAppStatus] = useState<WhatsAppConnectionStatus | null>(null)
  const [marketError, setMarketError] = useState<string | null>(null)
  const [reportError, setReportError] = useState<string | null>(null)
  const [aiError, setAiError] = useState<string | null>(null)
  const [recipientError, setRecipientError] = useState<string | null>(null)

  useEffect(() => {
    marketDataApi.schedule()
      .then(setMarketSchedule)
      .catch((requestError) => setMarketError(requestError instanceof ApiRequestError ? requestError.message : t('market.scheduleLoadError')))
    reportApi.schedule()
      .then(setReportSchedule)
      .catch((requestError) => setReportError(requestError instanceof ApiRequestError ? requestError.message : t('settings.reportLoadError')))
    reportApi.aiInfo()
      .then(setAiSettings)
      .catch((requestError) => setAiError(requestError instanceof ApiRequestError ? requestError.message : t('settings.aiLoadError')))
    reportApi.whatsAppRecipients()
      .then(setRecipients)
      .catch((requestError) => setRecipientError(requestError instanceof ApiRequestError ? requestError.message : t('settings.whatsappLoadError')))
    reportApi.whatsAppStatus().then(setWhatsAppStatus).catch(() => undefined)
  }, [t])

  useEffect(() => {
    const timer = window.setInterval(() => {
      void reportApi.whatsAppStatus().then(setWhatsAppStatus).catch(() => undefined)
    }, 5000)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <div className="settings-page page-enter">
      <header className="page-heading">
        <div>
          <span className="eyebrow">{t('settings.eyebrow')}</span>
          <h1>{t('settings.title')}</h1>
          <p>{t('settings.subtitle')}</p>
        </div>
      </header>

      {reportSchedule && (
        <OperationalSummary
          schedule={reportSchedule}
          whatsAppStatus={whatsAppStatus}
          selectedPortfolios={portfolios.filter((portfolio) => portfolio.scheduledReportEnabled).length}
        />
      )}

      <section className="settings-section" aria-labelledby="report-settings-title">
        <header className="settings-section__heading">
          <span className="eyebrow">{t('settings.automationEyebrow')}</span>
          <h2 id="report-settings-title">{t('settings.reportTitle')}</h2>
          <p>{t('settings.reportBody')}</p>
        </header>

        {!reportSchedule && !reportError && <LoadingSettings />}
        {reportError && <SettingsError message={reportError} />}
        <PortfolioReportSelection portfolios={portfolios} onUpdate={updateScheduledReport} />
        {reportSchedule && (
          <SchedulePanel
            schedule={reportSchedule}
            title={t('settings.reportScheduleTitle')}
            body={t('settings.reportScheduleBody')}
            timezoneEditable
            savedMessage={t('settings.reportSaved')}
            errorMessage={t('settings.reportSaveError')}
            onSave={reportApi.updateSchedule}
            onSaved={setReportSchedule}
          />
        )}
        {!aiSettings && !aiError && <LoadingSettings />}
        {aiError && <SettingsError message={aiError} />}
        {aiSettings && <AiSettingsPanel settings={aiSettings} onSaved={setAiSettings} />}
      </section>

      <section className="settings-section" aria-labelledby="whatsapp-settings-title">
        <header className="settings-section__heading">
          <span className="eyebrow">{t('settings.deliveryEyebrow')}</span>
          <h2 id="whatsapp-settings-title">{t('settings.whatsappTitle')}</h2>
          <p>{t('settings.whatsappBody')}</p>
        </header>
        {!recipients && !recipientError && <LoadingSettings />}
        {recipientError && <SettingsError message={recipientError} />}
        {recipients && <WhatsAppRecipientsPanel recipients={recipients} status={whatsAppStatus} onChange={setRecipients} />}
      </section>

      <section className="settings-section" aria-labelledby="market-settings-title">
        <header className="settings-section__heading">
          <span className="eyebrow">{t('settings.automationEyebrow')}</span>
          <h2 id="market-settings-title">{t('settings.marketTitle')}</h2>
          <p>{t('settings.marketBody')}</p>
        </header>

        {!marketSchedule && !marketError && <LoadingSettings />}
        {marketError && <SettingsError message={marketError} />}
        {marketSchedule && (
          <SchedulePanel
            schedule={marketSchedule}
            title={t('market.scheduleTitle')}
            body={t('market.scheduleBody')}
            savedMessage={t('market.scheduleSaved')}
            errorMessage={t('market.scheduleSaveError')}
            onSave={({ enabled, dayOfWeek, runTime }) => marketDataApi.updateSchedule({ enabled, dayOfWeek, runTime })}
            onSaved={setMarketSchedule}
          />
        )}
      </section>
    </div>
  )
}

function OperationalSummary({
  schedule,
  whatsAppStatus,
  selectedPortfolios,
}: {
  schedule: PortfolioReportSchedule
  whatsAppStatus: WhatsAppConnectionStatus | null
  selectedPortfolios: number
}) {
  const { t } = useTranslation()
  const lastStatus = schedule.lastRunStatus ? t(`market.runStatus.${schedule.lastRunStatus}`) : t('settings.noRuns')
  return (
    <section className="settings-summary" aria-labelledby="settings-summary-title">
      <header><span className="eyebrow">{t('settings.statusEyebrow')}</span><h2 id="settings-summary-title">{t('settings.statusTitle')}</h2></header>
      <div className="settings-summary__grid">
        <article><Clock3 size={20} /><span>{t('settings.nextReport')}</span><strong>{schedule.nextRunAt ? formatDateTime(schedule.nextRunAt, schedule.timezone) : t('market.noNextRun')}</strong></article>
        <article><FileText size={20} /><span>{t('settings.lastResult')}</span><strong>{lastStatus}</strong><small>{schedule.lastRunMessage ?? t('settings.noRunsBody')}</small></article>
        <article><MessageCircleMore size={20} /><span>{t('settings.whatsappStatus')}</span><strong>{t(`reports.whatsappState.${whatsAppStatus?.state ?? 'STARTING'}`)}</strong><small>{whatsAppStatus?.message}</small></article>
        <article><BriefcaseBusiness size={20} /><span>{t('settings.selectedPortfolios')}</span><strong>{t('settings.portfolioCount', { count: selectedPortfolios })}</strong></article>
      </div>
      <div className="settings-summary__actions">
        <Link className="quiet-button" to="/mercado"><RefreshCw size={16} />{t('settings.updatePrices')}</Link>
        <Link className="quiet-button" to="/informes"><FileText size={16} />{t('settings.generateReport')}</Link>
      </div>
    </section>
  )
}

function PortfolioReportSelection({
  portfolios,
  onUpdate,
}: {
  portfolios: PortfolioDefinition[]
  onUpdate: (id: number, enabled: boolean) => Promise<PortfolioDefinition>
}) {
  const { t } = useTranslation()
  const [workingId, setWorkingId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  async function toggle(portfolio: PortfolioDefinition) {
    setWorkingId(portfolio.id)
    setMessage(null)
    try {
      await onUpdate(portfolio.id, !portfolio.scheduledReportEnabled)
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.portfolioSaveError'))
    } finally {
      setWorkingId(null)
    }
  }

  return (
    <div className="portfolio-report-settings">
      <div className="market-schedule-card__intro"><span><BriefcaseBusiness size={20} /></span><div><h3>{t('settings.portfoliosTitle')}</h3><p>{t('settings.portfoliosBody')}</p></div></div>
      <div className="portfolio-report-settings__list">
        {portfolios.map((portfolio) => (
          <label className="schedule-switch" key={portfolio.id}>
            <input type="checkbox" checked={portfolio.scheduledReportEnabled} disabled={workingId === portfolio.id} onChange={() => void toggle(portfolio)} />
            <span>{portfolio.name}</span>
          </label>
        ))}
      </div>
      {message && <strong className="settings-feedback" role="alert">{message}</strong>}
    </div>
  )
}

function WhatsAppRecipientsPanel({
  recipients,
  status,
  onChange,
}: {
  recipients: WhatsAppRecipient[]
  status: WhatsAppConnectionStatus | null
  onChange: (recipients: WhatsAppRecipient[]) => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [adding, setAdding] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAdding(true)
    setMessage(null)
    try {
      const created = await reportApi.createWhatsAppRecipient({ name, phoneNumber, enabled: true })
      onChange(sortRecipients([...recipients, created]))
      setName('')
      setPhoneNumber('')
      setMessage(t('settings.whatsappCreated'))
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.whatsappSaveError'))
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="whatsapp-settings-card">
      <div className="whatsapp-settings-card__heading">
        <span><MessageCircleMore size={21} /></span>
        <div><h3>{t('settings.whatsappRecipients')}</h3><p>{t('settings.whatsappTestHelp')}</p></div>
        <strong className={status?.ready ? 'is-ready' : ''}>{t(`reports.whatsappState.${status?.state ?? 'STARTING'}`)}</strong>
      </div>
      <form className="whatsapp-recipient-add" aria-label={t('settings.whatsappAdd')} onSubmit={add}>
        <label><span>{t('settings.recipientName')}</span><input value={name} onChange={(event) => setName(event.target.value)} required maxLength={80} /></label>
        <label><span>{t('settings.recipientNumber')}</span><input type="tel" inputMode="tel" placeholder="573001234567" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} required maxLength={30} /></label>
        <button className="secondary-button" type="submit" disabled={adding}>{adding ? <LoaderCircle className="spin" size={16} /> : <MessageCircleMore size={16} />}{t('settings.whatsappAdd')}</button>
      </form>
      {message && <strong className="settings-feedback">{message}</strong>}
      <div className="whatsapp-recipient-list">
        {!recipients.length && <p className="whatsapp-recipient-empty">{t('settings.whatsappEmpty')}</p>}
        {recipients.map((recipient) => (
          <WhatsAppRecipientRow
            key={recipient.id}
            recipient={recipient}
            ready={Boolean(status?.ready)}
            onUpdated={(updated) => onChange(sortRecipients(recipients.map((item) => item.id === updated.id ? updated : item)))}
            onDeleted={() => onChange(recipients.filter((item) => item.id !== recipient.id))}
          />
        ))}
      </div>
    </div>
  )
}

function WhatsAppRecipientRow({
  recipient,
  ready,
  onUpdated,
  onDeleted,
}: {
  recipient: WhatsAppRecipient
  ready: boolean
  onUpdated: (recipient: WhatsAppRecipient) => void
  onDeleted: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(recipient.name)
  const [phoneNumber, setPhoneNumber] = useState(recipient.phoneNumber)
  const [enabled, setEnabled] = useState(recipient.enabled)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setWorking(true)
    setMessage(null)
    try {
      onUpdated(await reportApi.updateWhatsAppRecipient(recipient.id, { name, phoneNumber, enabled }))
      setMessage(t('settings.whatsappSaved'))
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.whatsappSaveError'))
    } finally {
      setWorking(false)
    }
  }

  async function test() {
    if (!window.confirm(t('settings.whatsappTestConfirm', { recipient: name }))) return
    setWorking(true)
    setMessage(null)
    try {
      await reportApi.testWhatsAppRecipient(recipient.id)
      setMessage(t('settings.whatsappTestSent'))
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.whatsappTestError'))
    } finally {
      setWorking(false)
    }
  }

  async function remove() {
    if (!window.confirm(t('settings.whatsappDeleteConfirm', { recipient: name }))) return
    setWorking(true)
    try {
      await reportApi.deleteWhatsAppRecipient(recipient.id)
      onDeleted()
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.whatsappDeleteError'))
      setWorking(false)
    }
  }

  return (
    <form className="whatsapp-recipient-row" aria-label={recipient.name} onSubmit={save}>
      <label><span>{t('settings.recipientName')}</span><input value={name} onChange={(event) => setName(event.target.value)} required maxLength={80} /></label>
      <label><span>{t('settings.recipientNumber')}</span><input type="tel" inputMode="tel" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} required maxLength={30} /></label>
      <label className="schedule-switch"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /><span>{t(enabled ? 'settings.recipientEnabled' : 'settings.recipientDisabled')}</span></label>
      <div className="whatsapp-recipient-actions">
        <button className="quiet-button" type="submit" disabled={working}><Save size={15} />{t('settings.recipientSave')}</button>
        <button className="quiet-button" type="button" disabled={working || !ready || !enabled} onClick={test}><Send size={15} />{t('settings.recipientTest')}</button>
        <button className="danger-button" type="button" disabled={working} onClick={remove} aria-label={t('settings.recipientDelete')}><Trash2 size={15} /></button>
      </div>
      {message && <strong className="settings-feedback">{message}</strong>}
    </form>
  )
}

function sortRecipients(recipients: WhatsAppRecipient[]) {
  return [...recipients].sort((left, right) => left.name.localeCompare(right.name))
}

function AiSettingsPanel({
  settings,
  onSaved,
}: {
  settings: PortfolioReportAiSettings
  onSaved: (settings: PortfolioReportAiSettings) => void
}) {
  const { t } = useTranslation()
  const [enabled, setEnabled] = useState(settings.enabled)
  const [model, setModel] = useState(settings.model)
  const [effort, setEffort] = useState(settings.effort)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const selectedModel = settings.models.find((option) => option.model === model)
  const efforts = selectedModel?.efforts.length ? selectedModel.efforts : [effort]

  function selectModel(value: string) {
    const option = settings.models.find((candidate) => candidate.model === value)
    setModel(value)
    if (option && !option.efforts.includes(effort)) {
      setEffort(option.defaultEffort || option.efforts[0])
    }
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setMessage(null)
    try {
      const updated = await reportApi.updateAiInfo({ enabled, model, effort })
      onSaved(updated)
      setMessage(t('settings.aiSaved'))
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : t('settings.aiSaveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="market-schedule-card ai-settings-card">
      <div className="market-schedule-card__intro"><span><Sparkles size={20} /></span><div><h3>{t('settings.aiTitle')}</h3><p>{t('settings.aiBody')}</p></div></div>
      <form aria-label={t('settings.aiTitle')} onSubmit={save}>
        <label className="schedule-switch"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /><span>{t(enabled ? 'settings.aiEnabled' : 'settings.aiDisabled')}</span></label>
        <label><span>{t('settings.aiModel')}</span><select value={model} onChange={(event) => selectModel(event.target.value)} disabled={!enabled}>{settings.models.map((option) => <option key={option.model} value={option.model}>{option.name}</option>)}</select></label>
        <label><span>{t('settings.aiEffort')}</span><select value={effort} onChange={(event) => setEffort(event.target.value)} disabled={!enabled}>{efforts.map((value) => <option key={value} value={value}>{t(`settings.efforts.${value}`)}</option>)}</select></label>
        <button className="secondary-button" type="submit" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}{t('settings.aiSave')}</button>
      </form>
      <div className="market-schedule-card__status">
        <span>{t(settings.catalogAvailable ? 'settings.aiCatalogAvailable' : 'settings.aiCatalogUnavailable')}</span>
        {message && <strong>{message}</strong>}
      </div>
    </div>
  )
}

function LoadingSettings() {
  const { t } = useTranslation()
  return <div className="page-state"><LoaderCircle className="spin" size={25} /><span>{t('settings.loading')}</span></div>
}

function SettingsError({ message }: { message: string }) {
  return <div className="page-state page-state--error"><AlertCircle size={28} /><strong>{message}</strong></div>
}

interface ScheduleForm {
  enabled: boolean
  dayOfWeek: WeekDay
  runTime: string
  timezone: string
}

interface EditableSchedule extends ScheduleForm {
  nextRunAt: string | null
  lastRunAt: string | null
  lastRunStatus: string | null
}

/** Editor compartido por las dos automatizaciones semanales. */
function SchedulePanel<T extends EditableSchedule>({
  schedule,
  title,
  body,
  timezoneEditable = false,
  savedMessage,
  errorMessage,
  onSave,
  onSaved,
}: {
  schedule: T
  title: string
  body: string
  timezoneEditable?: boolean
  savedMessage: string
  errorMessage: string
  onSave: (schedule: ScheduleForm) => Promise<T>
  onSaved: (schedule: T) => void
}) {
  const { t } = useTranslation()
  const [enabled, setEnabled] = useState(schedule.enabled)
  const [dayOfWeek, setDayOfWeek] = useState<WeekDay>(schedule.dayOfWeek)
  const [runTime, setRunTime] = useState(schedule.runTime.slice(0, 5))
  const [timezone, setTimezone] = useState(schedule.timezone)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function saveSchedule(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setMessage(null)
    try {
      const updated = await onSave({ enabled, dayOfWeek, runTime, timezone })
      onSaved(updated)
      setMessage(savedMessage)
    } catch (requestError) {
      setMessage(requestError instanceof ApiRequestError ? requestError.message : errorMessage)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={`market-schedule-card${timezoneEditable ? ' report-settings-card' : ''}`}>
      <div className="market-schedule-card__intro"><span><Clock3 size={20} /></span><div><h3>{title}</h3><p>{body}</p></div></div>
      <form onSubmit={saveSchedule}>
        <label className="schedule-switch"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /><span>{t(enabled ? 'market.scheduleEnabled' : 'market.scheduleDisabled')}</span></label>
        <label><span>{t('market.scheduleDay')}</span><select value={dayOfWeek} onChange={(event) => setDayOfWeek(event.target.value as WeekDay)}>{(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as WeekDay[]).map((day) => <option key={day} value={day}>{t(`market.days.${day}`)}</option>)}</select></label>
        <label><span>{t('market.scheduleTime')}</span><input type="time" value={runTime} onChange={(event) => setRunTime(event.target.value)} required /></label>
        {timezoneEditable && <label className="schedule-timezone"><span>{t('settings.timezone')}</span><input list="report-timezones" value={timezone} onChange={(event) => setTimezone(event.target.value)} required /><datalist id="report-timezones"><option value="America/Bogota" /><option value="America/Lima" /><option value="America/Mexico_City" /><option value="America/New_York" /><option value="Europe/Madrid" /><option value="UTC" /></datalist></label>}
        <button className="secondary-button" type="submit" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}{t('market.saveSchedule')}</button>
      </form>
      <div className="market-schedule-card__status">
        <span>{t('market.scheduleTimezone', { timezone: schedule.timezone })}</span>
        <span>{schedule.nextRunAt ? t('market.nextRun', { date: formatDateTime(schedule.nextRunAt, schedule.timezone) }) : t('market.noNextRun')}</span>
        {schedule.lastRunAt && <span>{t('market.lastRun', { date: formatDateTime(schedule.lastRunAt, schedule.timezone), status: t(`market.runStatus.${schedule.lastRunStatus}`) })}</span>}
        {message && <strong>{message}</strong>}
      </div>
    </div>
  )
}
