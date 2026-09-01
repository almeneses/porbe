import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { AppLoader } from '../components/AppLoader'
import { PortfolioProvider } from '../portfolio/PortfolioProvider'

/** Protege el área privada y conserva el destino solicitado para el login. */
export function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <AppLoader />
  if (!user) return <Navigate to="/iniciar-sesion" replace state={{ from: location.pathname }} />
  return <PortfolioProvider><Outlet /></PortfolioProvider>
}
