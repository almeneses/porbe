/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { apiRequest } from '../auth/api'
import { AppLoader } from '../components/AppLoader'
import { useTranslation } from 'react-i18next'

const ACTIVE_PORTFOLIO_KEY = 'porbe.activePortfolioId'

export interface PortfolioDefinition {
  id: number
  name: string
  baseCurrency: string
  createdAt: string
  updatedAt: string
}

interface PortfolioContextValue {
  portfolios: PortfolioDefinition[]
  activePortfolio: PortfolioDefinition
  selectPortfolio: (id: number) => void
  createPortfolio: (name: string) => Promise<PortfolioDefinition>
  renamePortfolio: (id: number, name: string) => Promise<PortfolioDefinition>
}

const PortfolioContext = createContext<PortfolioContextValue | null>(null)

/** Mantiene una única selección de portafolio compartida por todas las pantallas privadas. */
export function PortfolioProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [portfolios, setPortfolios] = useState<PortfolioDefinition[]>([])
  const [activeId, setActiveId] = useState<number | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let active = true
    apiRequest<PortfolioDefinition[]>('/api/portfolios')
      .then((items) => {
        if (!active || items.length === 0) return
        const storedId = Number(window.localStorage.getItem(ACTIVE_PORTFOLIO_KEY))
        const selected = items.find((item) => item.id === storedId) ?? items[0]
        setPortfolios(items)
        setActiveId(selected.id)
      })
      .catch(() => { if (active) setError(true) })
    return () => { active = false }
  }, [])

  const activePortfolio = portfolios.find((portfolio) => portfolio.id === activeId) ?? null

  const value = useMemo<PortfolioContextValue | null>(() => {
    if (!activePortfolio) return null
    const selectPortfolio = (id: number) => {
      if (!portfolios.some((portfolio) => portfolio.id === id)) return
      window.localStorage.setItem(ACTIVE_PORTFOLIO_KEY, String(id))
      setActiveId(id)
    }
    const createPortfolio = async (name: string) => {
      const created = await apiRequest<PortfolioDefinition>('/api/portfolios', {
        method: 'POST',
        body: JSON.stringify({ name }),
      })
      setPortfolios((current) => [...current, created])
      window.localStorage.setItem(ACTIVE_PORTFOLIO_KEY, String(created.id))
      setActiveId(created.id)
      return created
    }
    const renamePortfolio = async (id: number, name: string) => {
      const renamed = await apiRequest<PortfolioDefinition>(`/api/portfolios/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name }),
      })
      setPortfolios((current) => current.map((portfolio) => portfolio.id === id ? renamed : portfolio))
      return renamed
    }
    return { portfolios, activePortfolio, selectPortfolio, createPortfolio, renamePortfolio }
  }, [activePortfolio, portfolios])

  if (error) return <div className="page-state page-state--error"><strong>{t('portfolio.loadError')}</strong></div>
  if (!value) return <AppLoader />
  return <PortfolioContext.Provider value={value}>{children}</PortfolioContext.Provider>
}

export function usePortfolio() {
  const context = useContext(PortfolioContext)
  if (!context) throw new Error('usePortfolio debe usarse dentro de PortfolioProvider.')
  return context
}
