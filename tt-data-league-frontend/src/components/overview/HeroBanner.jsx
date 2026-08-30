import AccentBar from '../ui/AccentBar.jsx'
import { useTranslation } from 'react-i18next'

function HeroBanner() {
  const { t } = useTranslation()
  return (
    <section className="hero-banner card">
      <span className="hero-circle-big" aria-hidden="true" />
      <span className="hero-circle-small" aria-hidden="true" />
      <div className="hero-banner-content">
        <p className="hero-kicker">{t('overview.welcome')}</p>
        <h1 className="hero-title">TT League</h1>
        <AccentBar />
        <p className="hero-description">{t('overview.description')}</p>
        <p className="hero-note">{t('overview.note')}</p>
      </div>
    </section>
  )
}

export default HeroBanner
