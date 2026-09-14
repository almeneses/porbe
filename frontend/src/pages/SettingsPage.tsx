import { AlertCircle, Clock3, LoaderCircle, Save, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiRequestError } from '../auth/api'
import { marketDataApi } from '../market/api'
import type { MarketDataSchedule, WeekDay } from '../market/api'
import { reportApi } from '../report/api'
import type { PortfolioReportAiSettings, PortfolioReportSchedule } from '../report/api'
import { formatDateTime } from '../utils/formatters'

/** Reúne las preferencias operativas que pueden modificarse desde la aplicación. */
export function SettingsPage() {
  const { t } = useTranslation()
  const [marketSchedule, setMarketSchedule] = useState<MarketDataSchedule | null>(null)
  const [reportSchedule, setReportSchedule] = useState<PortfolioReportSchedule | null>(null)
  const [aiSettings, setAiSettings] = useState<PortfolioReportAiSettings | null>(null)
  const [marketError, setMarketError] = useState<string | null>(null)
  const [reportError, setReportError] = useState<string | null>(null)
  const [aiError, setAiError] = useState<string | null>(null)

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
  }, [t])

  return (
    <div className="settings-page page-enter">
      <header className="page-heading">
        <div>
          <span className="eyebrow">{t('settings.eyebrow')}</span>
          <h1>{t('settings.title')}</h1>
          <p>{t('settings.subtitle')}</p>
        </div>
      </header>

      <section className="settings-section" aria-labelledby="report-settings-title">
        <header className="settings-section__heading">
          <span className="eyebrow">{t('settings.automationEyebrow')}</span>
          <h2 id="report-settings-title">{t('settings.reportTitle')}</h2>
          <p>{t('settings.reportBody')}</p>
        </header>

        {!reportSchedule && !reportError && <LoadingSettings />}
        {reportError && <SettingsError message={reportError} />}
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
