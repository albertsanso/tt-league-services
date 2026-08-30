import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerSearch } from '../hooks/usePlayers.js'
import { useTranslation } from 'react-i18next'

function PlayersSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''
  const [inputValue, setInputValue] = useState(urlQuery)
  const { t } = useTranslation()
  const query = urlQuery.trim()
  const { data: players, loading, error, retry } = usePlayerSearch(query)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setInputValue(urlQuery), 0)
    return () => window.clearTimeout(timeoutId)
  }, [urlQuery])
  useEffect(() => {
    const normalized = inputValue.trim()
    if (normalized === urlQuery) return undefined
    const timeoutId = window.setTimeout(() => {
      const next = new URLSearchParams(searchParams)
      if (normalized) next.set('q', normalized)
      else next.delete('q')
      setSearchParams(next)
    }, 300)
    return () => window.clearTimeout(timeoutId)
  }, [inputValue, searchParams, setSearchParams, urlQuery])

  function submit(event) {
    event.preventDefault()
    const normalized = inputValue.trim()
    if (normalized.length < 2) return
    const next = new URLSearchParams(searchParams)
    next.set('q', normalized)
    setSearchParams(next)
  }

  return (
    <section className="page-block" aria-labelledby="players-title">
      <div>
        <p className="section-label">{t('search.directory')}</p>
        <h1 id="players-title" className="page-title">{t('search.playerTitle')}</h1>
        <p className="page-description">{t('search.playerDescription')}</p>
      </div>
      <form className="club-search-form" onSubmit={submit}>
        <label className="sr-only" htmlFor="player-search">{t('search.fieldPlayer')}</label>
        <div className="club-search-input-wrap">
          <Search size={17} aria-hidden="true" />
          <input
            id="player-search"
            className="club-search-input"
            type="search"
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
            placeholder={t('search.byName')}
            autoComplete="off"
            aria-describedby="player-search-help"
          />
        </div>
        <button className="primary-button" type="submit" disabled={inputValue.trim().length < 2}>{t('common.search')}</button>
      </form>
      <p id="player-search-help" className="search-summary" aria-live="polite">
        {t('overview.minCharacters')}
      </p>
      {!query ? (
        <p className="club-state card" role="status">{t('search.writePlayer')}</p>
      ) : query.length < 2 ? (
        <p className="club-state card" role="alert">{t('overview.minCharacters')}</p>
      ) : loading ? (
        <p className="club-state card" role="status" aria-live="polite">{t('search.searchingPlayers')}</p>
      ) : error ? (
        <div className="club-state card" role="alert">
          <p>{error.status === 401 ? t('search.sessionExpired') : t('search.playersLoadError')}</p>
          <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
        </div>
      ) : players.length === 0 ? (
        <p className="club-state card" role="status">{t('search.noPlayers', { query })}</p>
      ) : (
        <ul className="club-result-list" aria-label={t('search.resultsPlayers')}>
          {players.map((player) => (
            <li key={player.id} className="club-result card">
              {player.canonicalPlayerId ? (
                <Link
                  className="club-result-link"
                  to={routePaths.playerDetails(
                    player.id,
                    `${player.sources.length === 1 ? `source=${encodeURIComponent(player.sources[0])}` : 'source=all'}&season=all`,
                  )}
                >
                  <span>
                    <strong>{player.name}</strong>
                     <span className="club-source">{t('search.sources', { sources: player.sources.join(', ') || player.source })}</span>
                    <span className="club-source">
                      {t('search.seasons', { seasons: player.seasons.length > 0 ? player.seasons.join(', ') : '—' })}
                    </span>
                  </span>
                  <span aria-hidden="true">→</span>
                </Link>
              ) : (
                <div className="club-result-link">
                  <span>
                    <strong>{player.name}</strong>
                   <span className="club-source">{t('search.sources', { sources: player.sources.join(', ') || player.source })}</span>
                   <span className="club-source">
                     {t('search.seasons', { seasons: player.seasons.length > 0 ? player.seasons.join(', ') : '—' })}
                   </span>
                   <span className="club-source">{t('search.canonicalPending')}</span>
                  </span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default PlayersSearchPage
