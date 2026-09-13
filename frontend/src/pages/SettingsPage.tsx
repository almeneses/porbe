import { AlertCircle, Clock3, LoaderCircle, Save } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiRequestError } from '../auth/api'
import { marketDataApi } from '../market/api'
import type { MarketDataSchedule, WeekDay } from '../market/api'
import { formatDateTime } from '../utils/formatters'

/** Reúne las preferencias operativas que pueden modificarse desde la aplicación. */
export function SettingsPage() {
  const { t } = useTranslation()
  const [schedule, setSchedule] = useState<MarketDataSchedule | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    marketDataApi.schedule()
      .then(setSchedule)
      .catch((requestError) => setError(requestError instanceof ApiRequestError ? requestError.message : t('market.scheduleLoadError')))
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

      <section className="settings-section" aria-labelledby="market-settings-title">
        <header className="settings-section__heading">
          <span className="eyebrow">{t('settings.automationEyebrow')}</span>
          <h2 id="market-settings-title">{t('settings.marketTitle')}</h2>
          <p>{t('settings.marketBody')}</p>
        </header>

        {!schedule && !error && <div className="page-state"><LoaderCircle className="spin" size={25} /><span>{t('settings.loading')}</span></div>}
        {error && <div className="page-state page-state--error"><AlertCircle size={28} /><strong>{error}</strong></div>}
        {schedule && <MarketSchedulePanel schedule={schedule} onSaved={setSchedule} />}
      </section>
    </div>
  )
}

/** Permite activar y modificar el día y hora de la actualización semanal. */
function MarketSchedulePanel({ schedule, onSaved }: { schedule: MarketDataSchedule; onSaved: (schedule: MarketDataSchedule) => void }) {
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
    <div className="market-schedule-card">
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
    </div>
  )
}
