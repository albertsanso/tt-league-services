import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

function AnalyticsBanner() {
  const { t } = useTranslation()
  return (
    <section className="analytics-banner">
      <div>
        <h2 className="analytics-title">{t('overview.analytics')}</h2>
        <p className="analytics-description">{t('overview.analyticsDescription')}</p>
      </div>
      <Link className="analytics-button" to="/settings">
        {t('overview.moreInformation')}
      </Link>
    </section>
  )
}

export default AnalyticsBanner
