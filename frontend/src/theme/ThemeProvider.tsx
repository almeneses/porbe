/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'

/** Preferencia visual explícita o delegada al sistema operativo. */
export type ThemePreference = 'light' | 'dark' | 'system'

interface ThemeContextValue {
  preference: ThemePreference
  resolvedTheme: 'light' | 'dark'
  setPreference: (preference: ThemePreference) => void
  toggle: () => void
}

const STORAGE_KEY = 'porbe-theme'
const ThemeContext = createContext<ThemeContextValue | null>(null)

function getInitialPreference(): ThemePreference {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' || saved === 'system' ? saved : 'system'
  } catch {
    return 'system'
  }
}

function systemTheme() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Sincroniza la preferencia persistida, el sistema y los tokens CSS activos. */
export function ThemeProvider({ children }: PropsWithChildren) {
  const [preference, setPreferenceState] = useState<ThemePreference>(getInitialPreference)
  const [system, setSystem] = useState<'light' | 'dark'>(systemTheme)
  const resolvedTheme = preference === 'system' ? system : preference

  useEffect(() => {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystem(query.matches ? 'dark' : 'light')
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      resolvedTheme === 'dark' ? '#0c1922' : '#f5f1e9',
    )
  }, [resolvedTheme])

  const setPreference = useCallback((next: ThemePreference) => {
    // El cambio visual no debe depender de que el navegador permita persistirlo.
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Algunos entornos privados bloquean storage; el tema sigue activo en memoria.
    }
    setPreferenceState(next)
  }, [])

  const toggle = useCallback(() => {
    setPreference(resolvedTheme === 'dark' ? 'light' : 'dark')
  }, [resolvedTheme, setPreference])

  const value = useMemo(
    () => ({ preference, resolvedTheme, setPreference, toggle }),
    [preference, resolvedTheme, setPreference, toggle],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

/** Expone el tema activo y las acciones permitidas a los componentes. */
export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme debe usarse dentro de ThemeProvider')
  return context
}
