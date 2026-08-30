import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'
import { useTranslation } from 'react-i18next'

function ForgotPasswordPage() {
  const { recoverPassword } = useAuth()
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await recoverPassword(email)
      setSent(true)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title={t('auth.recoveryTitle')}
      description={t('auth.recoveryDescription')}
    >
      {sent ? (
        <p className="form-success" role="status">
          {t('auth.recoverySent')}
        </p>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <AuthField
            id="email"
            label="auth.email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
          <button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
            {isSubmitting ? t('auth.sending') : t('auth.sendInstructions')}
          </button>
        </form>
      )}
      <div className="auth-links">
        <Link to="/login">{t('auth.backLogin')}</Link>
      </div>
    </AuthLayout>
  )
}

export default ForgotPasswordPage
