import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useClubSearch } from '../hooks/useClubs.js'
import { useTranslation } from 'react-i18next'

function ClubsSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''
  const [inputValue, setInputValue] = useState(urlQuery)
  const [validationError, setValidationError] = useState('')
  const { t } = useTranslation()
  const query = urlQuery.trim()
  const { data: clubs, loading, error, retry } = useClubSearch(query)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setInputValue(urlQuery), 0)
    return () => window.clearTimeout(timeoutId)
  }, [urlQuery])

  useEffect(() => {
    const normalizedValue = inputValue.trim()
    if (normalizedValue === urlQuery) {
      return undefined
    }

    const timeoutId = window.setTimeout(() => {
      if (normalizedValue) {
        setSearchParams({ q: normalizedValue })
      } else {
        setSearchParams({})
      }
    }, 300)

    return () => window.clearTimeout(timeoutId)
  }, [inputValue, setSearchParams, urlQuery])

  function handleSubmit(event) {
    event.preventDefault()
    const normalizedValue = inputValue.trim()
    if (normalizedValue.length < 2) {
      setValidationError(t('overview.minCharacters'))
      return
    }

    setValidationError('')
    setSearchParams({ q: normalizedValue })
  }

  const hasQuery = query.length > 0
  const canSearch = inputValue.trim().length >= 2
  const displaySources = (club) => club.sources?.length ? club.sources : [club.source]

  return (
    <section className="page-block" aria-labelledby="clubs-title">
      <div>
        <p className="section-label">{t('search.directory')}</p>
        <h1 id="clubs-title" className="page-title">{t('search.clubTitle')}</h1>
        <p className="page-description">{t('search.clubDescription')}</p>
      </div>

      <form className="club-search-form" onSubmit={handleSubmit}>
        <label className="sr-only" htmlFor="club-search">{t('search.fieldClub')}</label>
        <div className="club-search-input-wrap">
          <Search size={17} aria-hidden="true" />
          <input
            id="club-search"
            className="club-search-input"
            type="search"
            value={inputValue}
            onChange={(event) => {
              setInputValue(event.target.value)
              setValidationError('')
            }}
            placeholder={t('search.byName')}
            autoComplete="off"
            aria-describedby="club-search-help"
          />
        </div>
        <button className="primary-button" type="submit" disabled={!canSearch}>
          {t('common.search')}
        </button>
      </form>
      <p id="club-search-help" className="search-summary" aria-live="polite">
        {validationError || t('overview.minCharacters')}
      </p>

      {!hasQuery ? (
        <p className="club-state card" role="status">
          {t('search.writeClub')}
        </p>
      ) : query.length < 2 ? (
        <p className="club-state card" role="alert">{t('overview.minCharacters')}</p>
      ) : loading ? (
        <p className="club-state card" role="status" aria-live="polite">{t('search.searchingClubs')}</p>
      ) : error ? (
        <div className="club-state card" role="alert">
          <p>{t('search.clubsLoadError')}</p>
          <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
        </div>
      ) : clubs.length === 0 ? (
        <p className="club-state card" role="status">
          {t('search.noClubs', { query })}
        </p>
      ) : (
        <ul className="club-result-list" aria-label={t('search.resultsClubs')}>
          {clubs.map((club) => (
            <li key={club.id} className="club-result card">
              <Link
                to={routePaths.clubDetails(club.id, 'season=all&source=all')}
                className="club-result-link"
              >
                <span>
                  <strong>{club.name}</strong>
                  <span className="club-source">
                    {displaySources(club).length > 1 ? t('common.sources') : t('common.source')}:{' '}
                    {displaySources(club).join(', ')}
                  </span>
                  {club.playerCount !== undefined ? (
                    <span className="club-source">
                      {t('search.playerCount', { count: club.playerCount, seasons: club.seasons?.length ?? 0 })}
                    </span>
                  ) : null}
                </span>
                <span aria-hidden="true">→</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ClubsSearchPage
