import { Link } from 'react-router-dom'

function AuthLayout({ title, description, children }) {
  return (
    <main className="auth-shell">
      <section className="auth-card" aria-labelledby="auth-title">
        <Link className="auth-brand" to="/" aria-label="TT League">
          <strong>TT</strong> League
        </Link>
        <h1 id="auth-title" className="page-title">{title}</h1>
        <p className="page-description">{description}</p>
        {children}
      </section>
    </main>
  )
}

export default AuthLayout
