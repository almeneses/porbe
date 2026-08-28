import { Logo } from './Logo'

/** Estado de carga global mostrado mientras se resuelve la sesión. */
export function AppLoader() {
  return (
    <main className="app-loader">
      <Logo />
      <span className="app-loader__pulse" aria-label="Cargando" />
    </main>
  )
}
