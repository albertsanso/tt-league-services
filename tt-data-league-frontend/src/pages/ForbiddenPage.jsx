import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

function ForbiddenPage() {
  const { t } = useTranslation()
  return (
    <main className="auth-shell">
      <section className="auth-card forbidden-card" aria-labelledby="forbidden-title">
        <p className="section-label">403</p>
        <h1 id="forbidden-title" className="page-title">{t('auth.forbidden')}</h1>
        <p className="page-description">{t('auth.forbiddenDescription')}</p>
        <Link className="primary-button" to="/">{t('auth.backHome')}</Link>
      </section>
    </main>
  )
}

export default ForbiddenPage
