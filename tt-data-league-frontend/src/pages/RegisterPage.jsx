import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthField from '../components/auth/AuthField.jsx'
import AuthLayout from '../components/auth/AuthLayout.jsx'
import { useAuth } from '../context/useAuth.js'

function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await register(form)
      navigate('/login', {
        replace: true,
        state: { message: 'Compte creat. Ja pots iniciar sessió.' },
      })
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Crea el teu compte"
      description="Registra’t per consultar la informació de la lliga."
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
          id="email"
          label="Correu electrònic"
          type="email"
          value={form.email}
          onChange={(event) => setForm({ ...form, email: event.target.value })}
          autoComplete="email"
        />
        <AuthField
          id="password"
          label="Contrasenya"
          type="password"
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
          autoComplete="new-password"
        />
        <button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creant compte...' : 'Crea el compte'}
        </button>
      </form>
      <div className="auth-links">
        <Link to="/login">Ja tinc un compte</Link>
      </div>
    </AuthLayout>
  )
}

export default RegisterPage
