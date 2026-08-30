import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'
import { useTranslation } from 'react-i18next'

function LoginPage() {
  const { login } = useAuth()
  const { t } = useTranslation()
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
      navigate('/', { replace: true })
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title={t('auth.loginTitle')}
      description={t('auth.loginDescription')}
    >
      <form className="auth-form" onSubmit={handleSubmit}>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <AuthField
          id="username"
          label="auth.username"
          value={form.username}
          onChange={(event) => setForm({ ...form, username: event.target.value })}
          autoComplete="username"
        />
        <AuthField
          id="password"
          label="auth.password"
          type="password"
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
          autoComplete="current-password"
        />
        <button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? t('auth.loggingIn') : t('auth.loginTitle')}
        </button>
      </form>
      <div className="auth-links">
        <Link to="/register">{t('auth.createAccount')}</Link>
        <Link to="/forgot-password">{t('auth.forgotPassword')}</Link>
      </div>
    </AuthLayout>
  )
}

export default LoginPage
