import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'
import { useTranslation } from 'react-i18next'

function ResetPasswordPage() {
  const { changePassword } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()
  const token = new URLSearchParams(location.search).get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)
  const [complete, setComplete] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    if (password !== confirmation) {
      setError(t('auth.passwordMismatch'))
      return
    }
    setSubmitting(true)
    try {
      await changePassword({ token, password })
      setComplete(true)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title={t('auth.newPassword')}
      description={t('auth.newPasswordDescription')}
    >
      {complete ? (
        <p className="form-success" role="status">
          {t('auth.passwordUpdated')}
        </p>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <AuthField
            id="password"
            label="auth.newPassword"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
          />
          <AuthField
            id="confirmation"
            label="auth.repeatPassword"
            type="password"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            autoComplete="new-password"
          />
          <button className="primary-button auth-submit" type="submit" disabled={isSubmitting || !token}>
            {isSubmitting ? t('auth.updating') : t('auth.updatePassword')}
          </button>
        </form>
      )}
      <div className="auth-links">
        <Link to="/login">{t('auth.backLogin')}</Link>
      </div>
    </AuthLayout>
  )
}

export default ResetPasswordPage
