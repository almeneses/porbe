import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { ImportPage } from './pages/ImportPage'
import { MarketDataPage } from './pages/MarketDataPage'
import { OperationsPage } from './pages/OperationsPage'
import { PlaceholderPage } from './pages/PlaceholderPage'

/** Define las rutas públicas y privadas que componen la aplicación. */
export function App() {
  return (
    <Routes>
      <Route path="/iniciar-sesion" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/resumen" replace />} />
          <Route path="/resumen" element={<DashboardPage />} />
          <Route path="/historico" element={<PlaceholderPage section="historico" />} />
          <Route path="/operaciones" element={<OperationsPage />} />
          <Route path="/importar" element={<ImportPage />} />
          <Route path="/mercado" element={<MarketDataPage />} />
          <Route path="/configuracion" element={<PlaceholderPage section="configuracion" />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/resumen" replace />} />
    </Routes>
  )
}
