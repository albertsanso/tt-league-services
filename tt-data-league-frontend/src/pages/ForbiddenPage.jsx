import { Link } from 'react-router-dom'

function ForbiddenPage() {
  return (
    <main className="auth-shell">
      <section className="auth-card forbidden-card" aria-labelledby="forbidden-title">
        <p className="section-label">403</p>
        <h1 id="forbidden-title" className="page-title">Accés no autoritzat</h1>
        <p className="page-description">
          No tens permisos per consultar aquesta pàgina.
        </p>
        <Link className="primary-button" to="/">Torna a l’inici</Link>
      </section>
    </main>
  )
}

export default ForbiddenPage
