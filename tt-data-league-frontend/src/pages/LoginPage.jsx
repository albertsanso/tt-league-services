import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'

function safeReturnPath(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : '/'
}

function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(form)
      navigate(safeReturnPath(location.state?.from), { replace: true })
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Inicia sessió"
      description="Accedeix a les dades de la lliga de tennis de taula."
    >
      <form className="auth-form" onSubmit={handleSubmit}>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <AuthField
          id="username"
          label="Usuari"
          value={form.username}
          onChange={(event) => setForm({ ...form, username: event.target.value })}
          autoComplete="username"
        />
        <AuthField
          id="password"
          label="Contrasenya"
          type="password"
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
          autoComplete="current-password"
        />
        <button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Accedint...' : 'Inicia sessió'}
        </button>
      </form>
      <div className="auth-links">
        <Link to="/register">Crea un compte</Link>
        <Link to="/forgot-password">Has oblidat la contrasenya?</Link>
      </div>
    </AuthLayout>
  )
}

export default LoginPage
