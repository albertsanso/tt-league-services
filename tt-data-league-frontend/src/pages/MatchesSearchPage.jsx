import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { getMatchOptions, searchMatches } from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'
import { routePaths } from '../config/routes.js'
import MatchActaDialog from '../components/matches/MatchActaDialog.jsx'

const sources = ['RFETM', 'FCTT', 'BCNESA']

function MatchesSearchPage() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [params, setParams] = useSearchParams()
  const [options, setOptions] = useState({ seasons: [], competitions: [] })
  const [results, setResults] = useState(null)
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [actaMatchId, setActaMatchId] = useState(null)
  const requestRef = useRef(null)
  const filters = useMemo(() => ({
    source: params.get('source') ?? '',
    season: params.get('season') ?? '',
    competition: params.get('competition') ?? '',
    fromDate: params.get('fromDate') ?? '',
    toDate: params.get('toDate') ?? '',
    playerLocation: params.get('playerLocation') ?? '',
    playerName: params.get('playerName') ?? '',
    clubName: params.get('clubName') ?? '',
  }), [params])
  const filterKey = useMemo(() => JSON.stringify(filters), [filters])

  useEffect(() => {
    requestRef.current?.abort()
  }, [
    filters.source,
    filters.season,
    filters.competition,
    filters.fromDate,
    filters.toDate,
    filters.playerLocation,
    filters.playerName,
    filters.clubName,
  ])

  useEffect(() => {
    requestRef.current?.abort()
    if (!filters.source) {
      return undefined
    }
    const controller = new AbortController()
    const request = Promise.resolve().then(() => {
      if (!controller.signal.aborted) setLoadingOptions(true)
      return getMatchOptions(filters.source, filters.season, token, controller.signal, clearSession)
    })
    request
      .then((value) => setOptions({
        seasons: Array.isArray(value.seasons) ? value.seasons : [],
        competitions: Array.isArray(value.competitions) ? value.competitions : [],
      }))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingOptions(false)
      })
    return () => controller.abort()
  }, [clearSession, filters.season, filters.source, token])

  const update = (key, value) => {
    requestRef.current?.abort()
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key === 'source') {
      next.delete('season')
      next.delete('competition')
    } else if (key === 'season') {
      next.delete('competition')
    }
    setResults(null)
    setError(null)
    if (key === 'source') setOptions({ seasons: [], competitions: [] })
    setParams(next)
  }

  const canSearch = filters.source && filters.season
  const search = () => {
    if (!canSearch) return
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setLoading(true)
    setError(null)
    searchMatches({ ...filters, page: 0 }, token, controller.signal, clearSession)
      .then((value) => setResults({ ...value, filterKey }))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
  }

  const goToPage = (page) => {
    const activeResults = results?.filterKey === filterKey ? results : null
    if (!activeResults || page < 0 || page === activeResults.page) return
    const controller = new AbortController()
    requestRef.current?.abort()
    requestRef.current = controller
    setLoading(true)
    setError(null)
    searchMatches({ ...filters, page }, token, controller.signal, clearSession)
      .then((value) => setResults({ ...value, filterKey }))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
  }

  const activeResults = results?.filterKey === filterKey ? results : null
  const totalPages = activeResults ? Math.max(1, Math.ceil(activeResults.total / activeResults.pageSize)) : 0

  const pagination = totalPages > 1 && activeResults ? (
    <nav className="pagination" aria-label={t('matchesPage.paginationAriaLabel')}>
      <button
        type="button"
        onClick={() => goToPage(0)}
        disabled={loading || activeResults.page === 0}
        aria-label={t('common.first')}
      >
        {t('common.first')}
      </button>
      <button
        type="button"
        onClick={() => goToPage(activeResults.page - 1)}
        disabled={loading || activeResults.page === 0}
        aria-label={t('common.previous')}
      >
        {t('common.previous')}
      </button>
      <span>{t('common.pageOf', { page: activeResults.page + 1, count: totalPages })}</span>
      <button
        type="button"
        onClick={() => goToPage(activeResults.page + 1)}
        disabled={loading || !activeResults.hasNext}
        aria-label={t('common.next')}
      >
        {t('common.next')}
      </button>
      <button
        type="button"
        onClick={() => goToPage(totalPages - 1)}
        disabled={loading || !activeResults.hasNext}
        aria-label={t('common.last')}
      >
        {t('common.last')}
      </button>
    </nav>
  ) : null

  return (
    <section className="page-block">
      <h1 className="page-title">{t('matchesPage.title')}</h1>
      <p className="page-description">{t('matchesPage.description')}</p>
      <article className="card match-filter-card">
        <h2>{t('matchesPage.filters')}</h2>
        <div className="match-filter-grid">
          <label className="match-filter-field">{t('matchesPage.source')}
            <select value={filters.source} onChange={(event) => update('source', event.target.value)}>
              <option value="">{t('matchesPage.allSources')}</option>
              {sources.map((source) => <option key={source} value={source}>{source}</option>)}
            </select>
          </label>
          <label className="match-filter-field">{t('matchesPage.season')}
            <select value={filters.season} disabled={!filters.source || loadingOptions} onChange={(event) => update('season', event.target.value)}>
              <option value="">{t('matchesPage.select')}</option>
              {options.seasons.map((season) => <option key={season} value={season}>{season}</option>)}
            </select>
          </label>
          <label className="match-filter-field">{t('matchesPage.competition')}
            <select value={filters.competition} disabled={!filters.season || loadingOptions} onChange={(event) => update('competition', event.target.value)}>
              <option value="">{t('matchesPage.select')}</option>
              {options.competitions.map((competition) => <option key={competition} value={competition}>{competition}</option>)}
            </select>
          </label>
          <div className="match-filter-field match-date-range">
            <span>{t('matchesPage.dateRange')}</span>
            <div className="match-date-fields">
              <label>
                <span className="sr-only">{t('matchesPage.fromDate')}</span>
                <input type="date" value={filters.fromDate} onChange={(event) => update('fromDate', event.target.value)} />
              </label>
              <span aria-hidden="true">—</span>
              <label>
                <span className="sr-only">{t('matchesPage.toDate')}</span>
                <input type="date" value={filters.toDate} onChange={(event) => update('toDate', event.target.value)} />
              </label>
            </div>
          </div>
          <fieldset className="match-player-location">
            <legend>{t('matchesPage.playerLocation')}</legend>
            <label>
              <input
                type="radio"
                name="playerLocation"
                value="HOME"
                checked={filters.playerLocation !== 'AWAY'}
                onChange={(event) => update('playerLocation', event.target.value)}
              />
              {t('matchesPage.home')}
            </label>
            <label>
              <input
                type="radio"
                name="playerLocation"
                value="AWAY"
                checked={filters.playerLocation === 'AWAY'}
                onChange={(event) => update('playerLocation', event.target.value)}
              />
              {t('matchesPage.away')}
            </label>
          </fieldset>
          <label className="match-filter-field match-player-name">{t('matchesPage.playerName')}
            <input value={filters.playerName} onChange={(event) => update('playerName', event.target.value)} />
          </label>
          <label className="match-filter-field match-club-name">{t('matchesPage.clubName')}
            <input value={filters.clubName} onChange={(event) => update('clubName', event.target.value)} />
          </label>
        </div>
        <button type="button" disabled={!canSearch || loading} onClick={search}>{loading ? t('matchesPage.loading') : t('common.search')}</button>
      </article>
      {error ? <p role="alert">{error.status === 401 ? t('matchesPage.unauthorized') : t('matchesPage.error')}</p> : null}
      {loading && !activeResults ? <p role="status">{t('matchesPage.loading')}</p> : null}
      {activeResults && activeResults.matches.length === 0 ? <p role="status">{t('matchesPage.empty')}</p> : null}
      {activeResults?.matches.length ? (
        <article className="card match-results-card">
          <h2>{t('matchesPage.results')}</h2>
          {pagination}
          <ul className="club-result-list" aria-label={t('matchesPage.results')}>
            {activeResults.matches.map((match) => {
              const hasActa = match.homeGamesWon != null && match.awayGamesWon != null
              return (
                <li key={match.id} className="club-result card">
                  <Link className="club-result-link" to={routePaths.matchSummary(match.id, params)}>
                    <span>
                      <strong>{match.homeTeam} – {match.awayTeam}</strong>
                      <span className="club-source">{[...(match.homePlayers ?? []), ...(match.awayPlayers ?? [])]
                        .map((player) => `${player.name} (${player.license ?? '—'})`).join(' · ')}</span>
                      <span className="club-source">{match.dateTime ? new Date(match.dateTime).toLocaleString() : t('common.unavailable')} · {match.competition}{match.phase ? ` · ${match.phase}` : ''} · {match.homeGamesWon ?? '—'}–{match.awayGamesWon ?? '—'}</span>
                    </span>
                  </Link>
                  {hasActa ? (
                    <button
                      type="button"
                      className="acta-view-link"
                      onClick={(event) => {
                        event.stopPropagation()
                        event.preventDefault()
                        setActaMatchId(match.id)
                      }}
                    >
                      {t('matchesPage.viewActa')}
                    </button>
                  ) : null}
                </li>
              )
            })}
          </ul>
          {pagination}
        </article>
      ) : null}
      {actaMatchId ? (
        <MatchActaDialog matchId={actaMatchId} onClose={() => setActaMatchId(null)} />
      ) : null}
    </section>
  )
}

export default MatchesSearchPage
