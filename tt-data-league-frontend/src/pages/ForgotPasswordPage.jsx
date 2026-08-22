import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'

function ForgotPasswordPage() {
  const { recoverPassword } = useAuth()
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
      title="Recupera la contrasenya"
      description="Indica el teu correu i t’enviarem instruccions si hi ha un compte associat."
    >
      {sent ? (
        <p className="form-success" role="status">
          Si el compte existeix, rebràs instruccions per recuperar la contrasenya.
        </p>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <AuthField
            id="email"
            label="Correu electrònic"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
          <button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Enviant...' : 'Envia instruccions'}
          </button>
        </form>
      )}
      <div className="auth-links">
        <Link to="/login">Torna a l’inici de sessió</Link>
      </div>
    </AuthLayout>
  )
}

export default ForgotPasswordPage
