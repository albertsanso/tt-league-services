import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'

function ResetPasswordPage() {
  const { changePassword } = useAuth()
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
      setError('Les contrasenyes no coincideixen.')
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
      title="Nova contrasenya"
      description="Tria una contrasenya nova per al teu compte."
    >
      {complete ? (
        <p className="form-success" role="status">
          La contrasenya s’ha actualitzat. Ja pots iniciar sessió.
        </p>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit}>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <AuthField
            id="password"
            label="Nova contrasenya"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
          />
          <AuthField
            id="confirmation"
            label="Repeteix la contrasenya"
            type="password"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            autoComplete="new-password"
          />
          <button className="primary-button auth-submit" type="submit" disabled={isSubmitting || !token}>
            {isSubmitting ? 'Actualitzant...' : 'Actualitza contrasenya'}
          </button>
        </form>
      )}
      <div className="auth-links">
        <Link to="/login">Torna a l’inici de sessió</Link>
      </div>
    </AuthLayout>
  )
}

export default ResetPasswordPage
