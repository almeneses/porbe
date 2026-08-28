import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useTheme } from '../theme/ThemeProvider'

/** Alterna de forma accesible entre las apariencias clara y oscura. */
export function ThemeToggle() {
  const { resolvedTheme, toggle } = useTheme()
  const { t } = useTranslation()
  const isDark = resolvedTheme === 'dark'

  return (
    <button
      className="icon-button"
      type="button"
      onClick={toggle}
      title={t(isDark ? 'theme.light' : 'theme.dark')}
      aria-label={t(isDark ? 'theme.light' : 'theme.dark')}
    >
      {isDark ? <Sun size={19} /> : <Moon size={19} />}
    </button>
  )
}
