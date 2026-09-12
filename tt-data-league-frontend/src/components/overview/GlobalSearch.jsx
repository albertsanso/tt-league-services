import { Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import SectionLabel from '../ui/SectionLabel.jsx'
import { useTranslation } from 'react-i18next'
import { routePaths } from '../../config/routes.js'
import { useGlobalSearch } from '../../hooks/useSearch.js'

const MAX_GROUP_RESULTS = 5

function playerDisplayName(player) {
  const licenses = [...new Set((player.federatedPlayers ?? [])
    .map((federatedPlayer) => federatedPlayer.license)
    .filter(Boolean))]
  return licenses.length > 0 ? `${player.name} (${licenses.join(', ')})` : player.name
}

function GlobalSearch() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef(null)
  const { data, loading } = useGlobalSearch(query)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedQuery(query.trim())
    }, 300)

    return () => window.clearTimeout(timeoutId)
  }, [query])

  useEffect(() => {
    function onOutsideClick(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', onOutsideClick)
    return () => document.removeEventListener('mousedown', onOutsideClick)
  }, [])

  const canSearch = query.trim().length >= 2
  const helperMessage = canSearch
    ? t('overview.searchHint')
    : t('overview.minCharacters')

  function onSubmit(event) {
    event.preventDefault()
    const normalizedQuery = debouncedQuery || query.trim()

    if (normalizedQuery.length < 2) {
      return
    }

    setIsOpen(false)
    navigate(`/cerca?q=${encodeURIComponent(normalizedQuery)}`)
  }

  const showDropdown = isOpen && canSearch
  const players = (data?.players ?? []).slice(0, MAX_GROUP_RESULTS)
  const clubs = (data?.clubs ?? []).slice(0, MAX_GROUP_RESULTS)
  const matches = (data?.matches ?? []).slice(0, MAX_GROUP_RESULTS)
  const hasAnyResult = players.length > 0 || clubs.length > 0 || matches.length > 0

  return (
    <section ref={containerRef}>
      <SectionLabel>{t('overview.globalSearch')}</SectionLabel>
      <form className="global-search-form" onSubmit={onSubmit}>
        <div className="global-search-field">
          <label className="sr-only" htmlFor="global-search">
            {t('overview.searchLabel')}
          </label>
          <Search className="global-search-icon" size={16} strokeWidth={1.5} aria-hidden="true" />
          <input
            id="global-search"
            className="global-search-input"
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setIsOpen(true)
            }}
            onFocus={() => setIsOpen(true)}
            onKeyDown={(event) => {
              if (event.key === 'Escape') setIsOpen(false)
            }}
            placeholder={t('overview.searchPlaceholder')}
            autoComplete="off"
            aria-describedby="global-search-help"
          />
          <p id="global-search-help" className="global-search-meta" aria-live="polite">
            {helperMessage}
          </p>
        </div>
        <button className="primary-button" type="submit" disabled={!canSearch}>
          {t('common.search')}
        </button>
      </form>
      {showDropdown ? (
        <div className="global-search-dropdown card" role="listbox" aria-label={t('overview.globalSearch')}>
          {loading ? (
            <p className="global-search-dropdown-status" role="status">{t('overview.searchLoading')}</p>
          ) : !hasAnyResult ? (
            <p className="global-search-dropdown-status" role="status">{t('overview.searchNoResults')}</p>
          ) : (
            <>
              <div className="global-search-dropdown-group">
                <p className="global-search-dropdown-heading">{t('search.resultsPlayers')}</p>
                {players.length === 0 ? (
                  <p className="global-search-dropdown-empty">{t('overview.searchGroupEmpty')}</p>
                ) : (
                  <ul className="global-search-dropdown-list">
                    {players.map((player) => (
                      <li key={player.id}>
                        {player.canonicalPlayerId ? (
                          <Link
                            className="global-search-dropdown-link"
                            onClick={() => setIsOpen(false)}
                            to={routePaths.playerDetails(
                              player.id,
                              `${player.sources?.length === 1 ? `source=${encodeURIComponent(player.sources[0])}` : 'source=all'}&season=all`,
                            )}
                          >
                            {playerDisplayName(player)}
                          </Link>
                        ) : (
                          <span className="global-search-dropdown-link global-search-dropdown-link-disabled">
                            {playerDisplayName(player)}
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="global-search-dropdown-group">
                <p className="global-search-dropdown-heading">{t('search.resultsClubs')}</p>
                {clubs.length === 0 ? (
                  <p className="global-search-dropdown-empty">{t('overview.searchGroupEmpty')}</p>
                ) : (
                  <ul className="global-search-dropdown-list">
                    {clubs.map((club) => (
                      <li key={club.id}>
                        <Link
                          className="global-search-dropdown-link"
                          onClick={() => setIsOpen(false)}
                          to={routePaths.clubDetails(club.id, 'season=all&source=all')}
                        >
                          {club.name}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="global-search-dropdown-group">
                <p className="global-search-dropdown-heading">{t('overview.resultsMatches')}</p>
                {matches.length === 0 ? (
                  <p className="global-search-dropdown-empty">{t('overview.searchGroupEmpty')}</p>
                ) : (
                  <ul className="global-search-dropdown-list">
                    {matches.map((match) => (
                      <li key={match.id}>
                        <Link
                          className="global-search-dropdown-link"
                          onClick={() => setIsOpen(false)}
                          to={routePaths.matchSummary(match.id)}
                        >
                          {match.homeTeam} – {match.awayTeam}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </div>
      ) : null}
    </section>
  )
}

export default GlobalSearch
