import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  LoaderCircle,
  ShieldCheck,
  UploadCloud,
  X,
} from 'lucide-react'
import { useRef, useState } from 'react'
import type { ChangeEvent, DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../auth/api'
import { portfolioApi } from '../portfolio/api'
import type { PortfolioImportResult } from '../portfolio/api'

const MAX_FILE_SIZE = 5 * 1024 * 1024

/** Gestiona la descarga de la plantilla y la importación validada del Excel. */
export function ImportPage() {
  const { t } = useTranslation()
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [dragActive, setDragActive] = useState(false)
  const [importing, setImporting] = useState(false)
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)
  const [result, setResult] = useState<PortfolioImportResult | null>(null)

  // Valida en el navegador los límites básicos antes de enviar el archivo.
  function selectFile(candidate?: File) {
    setResult(null)
    setRequestError(null)
    if (!candidate) {
      setFile(null)
      return
    }
    if (!candidate.name.toLowerCase().endsWith('.xlsx')) {
      setFile(null)
      setSelectionError(t('import.errors.format'))
      return
    }
    if (candidate.size > MAX_FILE_SIZE) {
      setFile(null)
      setSelectionError(t('import.errors.size'))
      return
    }
    setSelectionError(null)
    setFile(candidate)
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    selectFile(event.target.files?.[0])
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setDragActive(false)
    selectFile(event.dataTransfer.files?.[0])
  }

  function clearFile() {
    setFile(null)
    setResult(null)
    setSelectionError(null)
    setRequestError(null)
    if (fileInput.current) fileInput.current.value = ''
  }

  // Mantiene separados los errores de transporte del reporte fila a fila.
  async function handleImport() {
    if (!file) return
    setImporting(true)
    setRequestError(null)
    setResult(null)
    try {
      setResult(await portfolioApi.importFile(file))
    } catch (error) {
      setRequestError(error instanceof ApiRequestError ? error.message : t('import.errors.generic'))
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="import-page page-enter">
      <header className="page-heading import-heading">
        <div>
          <span className="eyebrow">{t('import.eyebrow')}</span>
          <h1>{t('import.title')}</h1>
          <p>{t('import.subtitle')}</p>
        </div>
      </header>

      <section className="import-steps" aria-label={t('import.stepsLabel')}>
        <article>
          <span>1</span>
          <div><strong>{t('import.stepOneTitle')}</strong><small>{t('import.stepOneBody')}</small></div>
        </article>
        <ArrowRight aria-hidden="true" />
        <article>
          <span>2</span>
          <div><strong>{t('import.stepTwoTitle')}</strong><small>{t('import.stepTwoBody')}</small></div>
        </article>
        <ArrowRight aria-hidden="true" />
        <article>
          <span>3</span>
          <div><strong>{t('import.stepThreeTitle')}</strong><small>{t('import.stepThreeBody')}</small></div>
        </article>
      </section>

      <section className="import-grid">
        <div className="import-main-card">
          <div className="import-main-card__heading">
            <span className="sheet-icon"><FileSpreadsheet size={24} /></span>
            <div>
              <h2>{t('import.uploadTitle')}</h2>
              <p>{t('import.uploadBody')}</p>
            </div>
          </div>

          <label
            className={`drop-zone${dragActive ? ' drop-zone--active' : ''}${file ? ' drop-zone--selected' : ''}`}
            htmlFor="portfolio-file"
            onDragEnter={(event) => { event.preventDefault(); setDragActive(true) }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
          >
            <input
              ref={fileInput}
              id="portfolio-file"
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={handleFileChange}
            />
            {file ? (
              <div className="selected-file">
                <span><FileSpreadsheet size={23} /></span>
                <div><strong>{file.name}</strong><small>{formatFileSize(file.size)}</small></div>
                <button
                  type="button"
                  aria-label={t('import.removeFile')}
                  onClick={(event) => { event.preventDefault(); clearFile() }}
                >
                  <X size={18} />
                </button>
              </div>
            ) : (
              <>
                <span className="drop-zone__icon"><UploadCloud size={28} /></span>
                <strong>{t('import.dropTitle')}</strong>
                <p>{t('import.dropBody')}</p>
                <small>{t('import.dropHint')}</small>
              </>
            )}
          </label>

          {selectionError && <div className="inline-alert inline-alert--error"><AlertCircle size={18} /> {selectionError}</div>}
          {requestError && <div className="inline-alert inline-alert--error"><AlertCircle size={18} /> {requestError}</div>}

          <button className="primary-button import-submit" type="button" disabled={!file || importing} onClick={handleImport}>
            {importing ? <LoaderCircle className="spin" size={19} /> : <UploadCloud size={19} />}
            {importing ? t('import.importing') : t('import.submit')}
          </button>

          {result && (
            <div className={`import-result${result.success ? ' import-result--success' : ' import-result--error'}`} role="status">
              <div className="import-result__heading">
                {result.success ? <CheckCircle2 size={22} /> : <AlertCircle size={22} />}
                <div>
                  <strong>{result.success ? t('import.successTitle') : t('import.errorTitle')}</strong>
                  <p>{result.message}</p>
                </div>
              </div>
              {result.errors.length > 0 && (
                <ul className="import-errors">
                  {result.errors.map((error, index) => (
                    <li key={`${error.row}-${error.field}-${index}`}>
                      <span>{error.row ? t('import.row', { row: error.row }) : t('import.fileLevel')}</span>
                      <div><strong>{error.field}</strong><p>{error.message}</p></div>
                    </li>
                  ))}
                </ul>
              )}
              {result.success && <Link className="result-link" to="/operaciones">{t('import.viewOperations')} <ArrowRight size={16} /></Link>}
            </div>
          )}
        </div>

        <aside className="import-side-card">
          <span className="download-icon"><Download size={23} /></span>
          <span className="eyebrow">{t('import.templateEyebrow')}</span>
          <h2>{t('import.templateTitle')}</h2>
          <p>{t('import.templateBody')}</p>
          <a className="secondary-button template-download" href={portfolioApi.templateUrl} download>
            <Download size={18} /> {t('import.download')}
          </a>
          <div className="import-guarantees">
            <div><ShieldCheck size={18} /><span><strong>{t('import.atomicTitle')}</strong><small>{t('import.atomicBody')}</small></span></div>
            <div><CheckCircle2 size={18} /><span><strong>{t('import.validatedTitle')}</strong><small>{t('import.validatedBody')}</small></span></div>
          </div>
        </aside>
      </section>
    </div>
  )
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
