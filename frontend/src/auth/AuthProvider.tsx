/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import { ApiRequestError, authApi } from './api'
import type { AuthenticatedUser } from './api'

interface AuthContextValue {
  user: AuthenticatedUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** Mantiene la sesión autenticada disponible para toda la interfaz. */
export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true

    // Prepara CSRF antes de recuperar una sesión que pudiera seguir vigente.
    async function initialize() {
      try {
        await authApi.prepare()
        const authenticatedUser = await authApi.me()
        if (active) setUser(authenticatedUser)
      } catch (error) {
        if (!(error instanceof ApiRequestError && error.status === 401)) {
          console.error(error)
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void initialize()
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const authenticatedUser = await authApi.login(username, password)
    setUser(authenticatedUser)
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** Expone el contexto de autenticación y evita su uso fuera del proveedor. */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return context
}
