import { ArrowRight, CheckCircle2, Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { Logo } from '../components/Logo'
import { ThemeToggle } from '../components/ThemeToggle'

/** Pantalla pública que autentica al usuario y lo devuelve a su ruta original. */
export function LoginPage() {
  const { t } = useTranslation()
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (user) return <Navigate to="/resumen" replace />

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username.trim(), password)
      const destination = (location.state as { from?: string } | null)?.from ?? '/resumen'
      navigate(destination, { replace: true })
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'No fue posible iniciar sesión.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-story" aria-label="Descripción de Porbe">
        <div className="login-story__glow login-story__glow--one" />
        <div className="login-story__glow login-story__glow--two" />
        <Logo />
        <div className="login-story__content">
          <span className="eyebrow eyebrow--inverse">{t('auth.eyebrow')}</span>
          <h1>{t('app.tagline')}<span>.</span></h1>
          <p>Convierte operaciones dispersas en una visión clara de tu patrimonio.</p>
          <ul>
            {['auth.featureOne', 'auth.featureTwo', 'auth.featureThree'].map((feature) => (
              <li key={feature}><CheckCircle2 size={18} /> {t(feature)}</li>
            ))}
          </ul>
        </div>
        <p className="login-story__foot">Diseñado para inversionistas en Colombia.</p>
      </section>

      <section className="login-panel">
        <div className="login-panel__theme"><ThemeToggle /></div>
        <div className="login-panel__mobile-brand"><Logo /></div>
        <form className="login-form" onSubmit={handleSubmit}>
          <span className="eyebrow">{t('auth.eyebrow')}</span>
          <h2>{t('auth.title')}</h2>
          <p className="login-form__subtitle">{t('auth.subtitle')}</p>

          <label className="field">
            <span>{t('auth.username')}</span>
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </label>
          <label className="field">
            <span>{t('auth.password')}</span>
            <span className="password-input">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
              <button type="button" onClick={() => setShowPassword((current) => !current)} aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </span>
          </label>

          {error && <div className="form-error" role="alert">{error}</div>}

          <button className="primary-button" type="submit" disabled={submitting}>
            <span>{t(submitting ? 'auth.submitting' : 'auth.submit')}</span>
            {!submitting && <ArrowRight size={18} />}
          </button>
          <p className="login-form__demo">{t('auth.demo')}</p>
        </form>
      </section>
    </main>
  )
}
