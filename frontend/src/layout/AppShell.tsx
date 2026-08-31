import { ChartNoAxesCombined, Clock3, FileImage, FileSpreadsheet, History, LayoutDashboard, LogOut, Settings } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { Logo } from '../components/Logo'
import { ThemeToggle } from '../components/ThemeToggle'

/** Navegación principal compartida por escritorio y móvil. */
const navigation = [
  { to: '/resumen', label: 'nav.summary', icon: LayoutDashboard },
  { to: '/historico', label: 'nav.history', icon: History },
  { to: '/operaciones', label: 'nav.operations', icon: Clock3 },
  { to: '/importar', label: 'nav.import', icon: FileSpreadsheet },
  { to: '/mercado', label: 'nav.market', icon: ChartNoAxesCombined },
  { to: '/informes', label: 'nav.reports', icon: FileImage },
  { to: '/configuracion', label: 'nav.settings', icon: Settings },
]

const mobileNavigation = [navigation[0], navigation[1], navigation[5], navigation[2]]

/** Distribuye el menú lateral, la navegación móvil y el contenido autenticado. */
export function AppShell() {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__top">
          <Logo />
          <nav className="sidebar__nav" aria-label="Navegación principal">
            {navigation.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}>
                <Icon size={19} />
                <span>{t(label)}</span>
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="sidebar__account">
          <span className="avatar">{user?.username.slice(0, 1).toUpperCase()}</span>
          <div className="sidebar__identity">
            <strong>{user?.username}</strong>
            <span>Administrador</span>
          </div>
          <button className="icon-button" onClick={handleLogout} disabled={loggingOut} aria-label={t('nav.logout')} title={t('nav.logout')}>
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      <div className="app-content">
        <header className="mobile-header">
          <Logo />
          <ThemeToggle />
        </header>
        <main className="page-content">
          <div className="desktop-theme"><ThemeToggle /></div>
          <Outlet />
        </main>
        <nav className="bottom-nav" aria-label="Navegación móvil">
          {mobileNavigation.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `bottom-nav__item${isActive ? ' bottom-nav__item--active' : ''}`}>
              <Icon size={20} />
              <span>{t(label)}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  )
}
