import { Link } from 'react-router-dom'

function AnalyticsBanner() {
  return (
    <section className="analytics-banner">
      <div>
        <h2 className="analytics-title">Analítica avançada</h2>
        <p className="analytics-description">
          Aviat podràs analitzar, comparar i descobrir patrons amb suport
          d&apos;intel·ligència artificial.
        </p>
      </div>
      <Link className="analytics-button" to="/settings">
        Més informació
      </Link>
    </section>
  )
}

export default AnalyticsBanner
