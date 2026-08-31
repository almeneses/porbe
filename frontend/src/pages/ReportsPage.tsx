import {
  AlertCircle,
  CalendarRange,
  CheckCircle2,
  Clock3,
  Download,
  FileImage,
  FileText,
  LoaderCircle,
  MessageCircleMore,
  Sparkles,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiRequestError } from '../auth/api'
import { reportApi } from '../report/api'
import type { PortfolioReport, PortfolioReportSchedule } from '../report/api'
import { formatDate, formatDateTime } from '../utils/formatters'

/** Genera, previsualiza y conserva los informes periódicos del portafolio. */
export function ReportsPage() {
  const { t } = useTranslation()
  const initialRange = useMemo(defaultReportRange, [])
  const [from, setFrom] = useState(initialRange.from)
  const [to, setTo] = useState(initialRange.to)
  const [reports, setReports] = useState<PortfolioReport[]>([])
  const [schedule, setSchedule] = useState<PortfolioReportSchedule | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const [items, configuredSchedule] = await Promise.all([reportApi.list(), reportApi.schedule()])
      setReports(items)
      setSchedule(configuredSchedule)
      setSelectedId((current) => current ?? items.find((item) => item.status === 'READY')?.id ?? null)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('reports.loadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { void load() }, [load])

  async function generate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setGenerating(true)
    setError(null)
    try {
      const generated = await reportApi.generate(from, to)
      setSelectedId(generated.id)
      await load()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : t('reports.generateError'))
    } finally {
      setGenerating(false)
    }
  }

  const selected = reports.find((report) => report.id === selectedId) ?? null

  return (
    <div className="reports-page page-enter">
      <header className="page-heading reports-heading">
        <div>
          <span className="eyebrow">{t('reports.eyebrow')}</span>
          <h1>{t('reports.title')}</h1>
          <p>{t('reports.subtitle')}</p>
        </div>
        {schedule && <ScheduleBadge schedule={schedule} />}
      </header>

      {error && <div className="inline-alert inline-alert--error"><AlertCircle size={19} /><strong>{error}</strong></div>}

      <section className="report-builder-card">
        <div className="report-builder-card__intro">
          <span><Sparkles size={23} /></span>
          <div><h2>{t('reports.builderTitle')}</h2><p>{t('reports.builderBody')}</p></div>
        </div>
        <form onSubmit={generate}>
          <label><span>{t('reports.from')}</span><input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} required /></label>
          <label><span>{t('reports.to')}</span><input type="date" value={to} min={from} max={todayInput()} onChange={(event) => setTo(event.target.value)} required /></label>
          <button className="secondary-button" type="submit" disabled={generating}>
            {generating ? <LoaderCircle className="spin" size={17} /> : <FileImage size={17} />}
            {t(generating ? 'reports.generating' : 'reports.generate')}
          </button>
        </form>
        <small>{t('reports.notesHidden')}</small>
      </section>

      {loading ? (
        <div className="page-state"><LoaderCircle className="spin" size={26} /><span>{t('reports.loading')}</span></div>
      ) : (
        <div className="report-workspace">
          <ReportPreview report={selected} />
          <ReportScheduleCard schedule={schedule} />
        </div>
      )}

      {!loading && <ReportHistory reports={reports} selectedId={selectedId} onSelect={setSelectedId} />}
    </div>
  )
}

function ReportPreview({ report }: { report: PortfolioReport | null }) {
  const { t } = useTranslation()
  if (!report) {
    return <section className="report-preview-card report-preview-card--empty"><span><FileImage size={28} /></span><h2>{t('reports.emptyPreviewTitle')}</h2><p>{t('reports.emptyPreviewBody')}</p></section>
  }
  return (
    <section className="report-preview-card">
      <header>
        <div><span className="eyebrow">{t('reports.previewEyebrow')}</span><h2>{t('reports.previewTitle')}</h2><p>{formatDate(report.from)} - {formatDate(report.to)}</p></div>
        <div className="report-downloads">
          <a className="quiet-button" href={reportApi.imageUrl(report.id, true)} download><Download size={16} /> PNG</a>
          <a className="secondary-button" href={reportApi.pdfUrl(report.id)} download><FileText size={16} /> PDF</a>
        </div>
      </header>
      {!report.valuationComplete && <div className="report-warning"><AlertCircle size={16} /><span>{t('reports.partialReport')}</span></div>}
      <div className="report-image-frame"><img src={reportApi.imageUrl(report.id)} alt={t('reports.previewAlt', { from: formatDate(report.from), to: formatDate(report.to) })} /></div>
    </section>
  )
}

function ScheduleBadge({ schedule }: { schedule: PortfolioReportSchedule }) {
  const { t } = useTranslation()
  return <div className="system-pill"><span />{t(schedule.enabled ? 'reports.scheduleActive' : 'reports.scheduleInactive')}</div>
}

function ReportScheduleCard({ schedule }: { schedule: PortfolioReportSchedule | null }) {
  const { t } = useTranslation()
  return (
    <aside className="report-schedule-panel">
      <span className="report-schedule-panel__icon"><Clock3 size={22} /></span>
      <span className="eyebrow">{t('reports.automationEyebrow')}</span>
      <h2>{t('reports.automationTitle')}</h2>
      <p>{t('reports.automationBody')}</p>
      <dl>
        <div><dt>{t('reports.schedule')}</dt><dd>{t('reports.fridayTime')}</dd></div>
        <div><dt>{t('reports.timezone')}</dt><dd>{schedule?.timezone ?? 'America/Bogota'}</dd></div>
        <div><dt>{t('reports.nextRun')}</dt><dd>{schedule?.nextRunAt ? formatDateTime(schedule.nextRunAt) : '—'}</dd></div>
      </dl>
      <div className="report-delivery-state">
        <MessageCircleMore size={19} />
        <div><strong>{t('reports.whatsappPending')}</strong><span>{t('reports.whatsappPendingBody')}</span></div>
      </div>
    </aside>
  )
}

function ReportHistory({ reports, selectedId, onSelect }: { reports: PortfolioReport[]; selectedId: number | null; onSelect: (id: number) => void }) {
  const { t } = useTranslation()
  return (
    <section className="report-history-section">
      <header className="history-section-heading"><div><span className="eyebrow">{t('reports.historyEyebrow')}</span><h2>{t('reports.historyTitle')}</h2><p>{t('reports.historyBody')}</p></div><CalendarRange size={23} /></header>
      {reports.length === 0 ? <div className="report-history-empty">{t('reports.noReports')}</div> : (
        <>
          <div className="report-history-table-wrap scrollable-table"><table className="report-history-table"><thead><tr><th>{t('reports.period')}</th><th>{t('reports.origin')}</th><th>{t('reports.generatedAt')}</th><th>{t('reports.valuation')}</th><th>{t('reports.delivery')}</th><th>{t('reports.actions')}</th></tr></thead><tbody>{reports.map((report) => <ReportHistoryRow key={report.id} report={report} selected={selectedId === report.id} onSelect={onSelect} />)}</tbody></table></div>
          <div className="report-history-cards">{reports.map((report) => <ReportHistoryCard key={report.id} report={report} selected={selectedId === report.id} onSelect={onSelect} />)}</div>
        </>
      )}
    </section>
  )
}

function ReportHistoryRow({ report, selected, onSelect }: { report: PortfolioReport; selected: boolean; onSelect: (id: number) => void }) {
  const { t } = useTranslation()
  return <tr className={selected ? 'is-selected' : ''}><td><strong>{formatDate(report.from)} - {formatDate(report.to)}</strong></td><td>{t(`reports.trigger.${report.triggerType}`)}</td><td>{report.generatedAt ? formatDateTime(report.generatedAt) : '—'}</td><td><ReportStatus report={report} /></td><td>{t(`reports.deliveryStatus.${report.deliveryStatus}`)}</td><td><button className="table-view-action" type="button" onClick={() => onSelect(report.id)}><FileImage size={15} />{t('reports.view')}</button></td></tr>
}

function ReportHistoryCard({ report, selected, onSelect }: { report: PortfolioReport; selected: boolean; onSelect: (id: number) => void }) {
  const { t } = useTranslation()
  return <article className={`report-history-card${selected ? ' is-selected' : ''}`}><div><strong>{formatDate(report.from)} - {formatDate(report.to)}</strong><span>{t(`reports.trigger.${report.triggerType}`)}</span></div><ReportStatus report={report} /><button type="button" onClick={() => onSelect(report.id)}>{t('reports.view')}</button></article>
}

function ReportStatus({ report }: { report: PortfolioReport }) {
  const { t } = useTranslation()
  const complete = report.status === 'READY' && report.valuationComplete
  return <span className={`report-status report-status--${complete ? 'complete' : report.status.toLowerCase()}`}>{complete ? <CheckCircle2 size={13} /> : <AlertCircle size={13} />}{t(complete ? 'reports.complete' : report.status === 'FAILED' ? 'reports.failed' : 'reports.partial')}</span>
}

function defaultReportRange() {
  const today = new Date()
  const friday = new Date(today)
  const daysSinceFriday = (today.getDay() + 2) % 7
  friday.setDate(today.getDate() - daysSinceFriday)
  const monday = new Date(friday)
  monday.setDate(friday.getDate() - 4)
  return { from: dateInput(monday), to: dateInput(friday) }
}

function todayInput() { return dateInput(new Date()) }

function dateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
