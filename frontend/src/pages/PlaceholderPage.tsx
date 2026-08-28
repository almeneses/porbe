import { Construction } from 'lucide-react'
import { useTranslation } from 'react-i18next'

/** Estado temporal para secciones previstas en incrementos posteriores. */
export function PlaceholderPage({ section }: { section: 'historico' | 'operaciones' | 'importar' | 'configuracion' }) {
  const { t } = useTranslation()
  return (
    <div className="placeholder-page page-enter">
      <span className="placeholder-page__icon"><Construction size={26} /></span>
      <span className="eyebrow">{t('placeholder.eyebrow')}</span>
      <h1>{t(`placeholder.${section}`)}</h1>
      <p>{t('placeholder.body')}</p>
    </div>
  )
}
