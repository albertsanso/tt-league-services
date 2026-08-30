import SectionLabel from '../components/ui/SectionLabel.jsx'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

function MatchesSearchPage() {
  const [searchParams] = useSearchParams()
  const clubId = searchParams.get('clubId')
  const { t } = useTranslation()

  return (
    <section className="page-block">
      <h1 className="page-title">{t('matchesPage.title')}</h1>
      <p className="page-description">{t('matchesPage.description')}</p>

      <SectionLabel>{t('matchesPage.building')}</SectionLabel>
      {clubId ? (
        <p className="selected-club-filter" role="status">
          {t('matchesPage.activeFilter', { id: clubId })}
        </p>
      ) : null}
      <article className="placeholder-panel card">
        <h2>{t('matchesPage.comingSoon')}</h2>
        <p>{t('matchesPage.body')}</p>
      </article>
    </section>
  )
}

export default MatchesSearchPage
