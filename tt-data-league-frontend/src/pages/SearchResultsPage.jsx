import { useSearchParams } from 'react-router-dom'
import Badge from '../components/ui/Badge.jsx'
import SectionLabel from '../components/ui/SectionLabel.jsx'
import { useTranslation } from 'react-i18next'

function SearchResultsPage() {
  const [searchParams] = useSearchParams()
  const query = (searchParams.get('q') || '').trim()
  const hasMinimumQuery = query.length >= 2
  const { t } = useTranslation()

  return (
    <section className="page-block">
      <h1 className="page-title">{t('results.title')}</h1>
      <p className="search-summary">{t('results.summary')}</p>

      <SectionLabel>{t('results.current')}</SectionLabel>
      <article className="placeholder-panel card">
        {hasMinimumQuery ? (
          <>
            <p className="search-summary">
              {t('results.searching', { query })}
            </p>
            <p>
              {t('results.future', { endpoint: '/api/cerca' })}
            </p>
          </>
        ) : (
          <>
            <p>{t('results.enter')}</p>
            <p>
              {t('results.example', { term: 'terrassa' })}{' '}
              <Badge tone="warning">{t('results.minimum')}</Badge>
            </p>
          </>
        )}
      </article>
    </section>
  )
}

export default SearchResultsPage
