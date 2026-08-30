import { Edit3, Swords, Users } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useLocation, useSearchParams, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails } from '../hooks/useClubs.js'
import { useTranslation } from 'react-i18next'

const VIEWS = {
  PLAYERS: 'players',
  MATCHES: 'matches',
}
const ALL_SEASONS = 'all'
const ALL_SOURCES = 'all'

function uniqueSorted(values) {
  return [...new Set(values.filter(Boolean))].sort()
}

function displaySources(club) {
  return club.sources?.length ? club.sources : [club.source]
}

function ClubDetailPage() {
  const { clubId } = useParams()
  const location = useLocation()
  const { hasRole } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const { data: club, loading, error, retry } = useClubDetails(clubId)
  const { t } = useTranslation()
  const requestedView = searchParams.get('view')
  const view = requestedView === VIEWS.PLAYERS ? VIEWS.PLAYERS : VIEWS.MATCHES
  const successMessage = searchParams.get('message') || location.state?.successMessage

  if (loading) {
    return <p className="club-state card" role="status" aria-live="polite">{t('detail.loadingClub')}</p>
  }

  if (error?.status === 404 || error?.status === 400) {
    return (
      <section className="page-block" role="alert" aria-labelledby="club-not-found-title">
        <p className="section-label">{t('common.clubs')}</p>
        <h1 id="club-not-found-title" className="page-title">{t('detail.clubNotFound')}</h1>
        <p className="page-description">{t('detail.clubNotFoundDescription')}</p>
        <Link className="secondary-button" to={routePaths.clubs}>{t('detail.backSearch')}</Link>
      </section>
    )
  }

  if (error || !club) {
    return (
      <section className="page-block" role="alert" aria-labelledby="club-error-title">
        <h1 id="club-error-title" className="page-title">{t('detail.clubLoadError')}</h1>
        <p className="page-description">{t('detail.clubLoadDescription')}</p>
        <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
      </section>
    )
  }

  return (
    <ClubDetailContent
      club={club}
      view={view}
      searchParams={searchParams}
      setSearchParams={setSearchParams}
      isAdmin={hasRole('ADMIN')}
      successMessage={successMessage}
      t={t}
    />
  )
}

function ClubDetailContent({
  club,
  view,
  searchParams,
  setSearchParams,
  isAdmin,
  successMessage,
  t,
}) {
  const sources = uniqueSorted([
    ...displaySources(club),
    ...club.teams.map((team) => team.source),
    ...club.players.map((player) => player.source),
  ].filter((source) => source !== '—'))
  const requestedSource = searchParams.get('source')
  const allSourcesSelected = requestedSource == null || requestedSource === ALL_SOURCES
  const sourceFilter = allSourcesSelected
    ? ''
    : sources.includes(requestedSource)
    ? requestedSource
    : sources[0] ?? ''
  const sourceMatches = (source) => !sourceFilter || source === sourceFilter
  const sourceTeams = club.teams.filter((team) => sourceMatches(team.source))
  const sourcePlayers = club.players.filter((player) => sourceMatches(player.source))
  const sourceCompetitions = club.competitions.filter((item) => sourceMatches(item.source ?? club.source))
  const seasons = uniqueSorted([
    ...sourceTeams.map((team) => team.season),
    ...sourceCompetitions.map((competition) => competition.season),
    ...sourcePlayers.map((player) => player.season),
  ])
  const requestedSeason = searchParams.get('season')
  const requestedCompetition = searchParams.get('competition')
  const allSeasonsSelected = requestedSeason === ALL_SEASONS
  const selectedCompetitionSeasons = uniqueSorted(
    sourceCompetitions
      .filter((item) => item.name === requestedCompetition)
      .map((item) => item.season),
  )
  const availableSeasons = requestedCompetition && selectedCompetitionSeasons.length > 0
    ? selectedCompetitionSeasons
    : seasons
  const competitionMatch = sourceCompetitions.find((item) => (
    item.name === requestedCompetition && item.season === requestedSeason
  )) ?? sourceCompetitions.find((item) => item.name === requestedCompetition)
  const season = allSeasonsSelected
    ? ''
    : availableSeasons.includes(requestedSeason)
    ? requestedSeason
    : competitionMatch?.season ?? availableSeasons[0] ?? ''
  const availableCompetitions = sourceCompetitions
    .filter((competition) => !season || competition.season === season)
    .filter((competition, index, values) => (
      values.findIndex((item) => item.name === competition.name) === index
    ))
    .sort((left, right) => left.name.localeCompare(right.name))
  const competition = availableCompetitions.some(({ name }) => name === requestedCompetition)
    ? requestedCompetition
    : ''
  const filteredCompetitions = sourceCompetitions.filter((item) => (
    (!season || item.season === season) && (!competition || item.name === competition)
  ))
  const players = sourcePlayers.filter((player) => (
    (!season || player.season === season)
      && (!competition || player.competitions.includes(competition))
  ))

  useEffect(() => {
    const normalizedParams = new URLSearchParams(searchParams)
    if (allSeasonsSelected) {
      normalizedParams.set('season', ALL_SEASONS)
    } else if (season) {
      normalizedParams.set('season', season)
    } else {
      normalizedParams.delete('season')
    }
    if (requestedSource === ALL_SOURCES) {
      normalizedParams.set('source', ALL_SOURCES)
    } else if (requestedSource != null && sourceFilter) {
      normalizedParams.set('source', sourceFilter)
    } else if (requestedSource != null) {
      normalizedParams.delete('source')
    }
    if (competition) {
      normalizedParams.set('competition', competition)
    } else {
      normalizedParams.delete('competition')
    }
    if (normalizedParams.toString() !== searchParams.toString()) {
      setSearchParams(normalizedParams, { replace: true })
    }
  }, [
    allSeasonsSelected,
    allSourcesSelected,
    competition,
    requestedSource,
    season,
    searchParams,
    setSearchParams,
    sourceFilter,
  ])

  function updateFilters(nextValues) {
    const next = new URLSearchParams(searchParams)
    Object.entries(nextValues).forEach(([key, value]) => {
      if (value) {
        next.set(key, value)
      } else {
        next.delete(key)
      }
    })
    setSearchParams(next)
  }

  return (
    <section className="page-block club-detail-page" aria-labelledby="club-detail-title">
      {successMessage ? <p className="form-success" role="status">{successMessage}</p> : null}
      <div className="club-detail-header">
        <div>
          <p className="section-label">{t('detail.identityClub')}</p>
          <h1 id="club-detail-title" className="page-title">{club.name}</h1>
          <p className="club-source">
            {displaySources(club).length > 1 ? t('common.sources') : t('common.source')}:{' '}
            {displaySources(club).join(', ')}
          </p>
          {club.federatedClubs?.length ? (
            <p className="club-source">
              {t('detail.federatedRecords', { records: club.federatedClubs
                .map((federatedClub) => `${federatedClub.name} (${federatedClub.source})`)
                .join(', ') })}
            </p>
          ) : null}
        </div>
        {isAdmin ? (
          <Link
            className="secondary-button"
            to={routePaths.clubEdit(club.id, searchParams)}
          >
            <Edit3 size={16} aria-hidden="true" /> {t('detail.editClub')}
          </Link>
        ) : null}
      </div>

      <div className="club-controls">
        <div className="club-tabs" role="tablist" aria-label={t('detail.clubViews')}>
          <button
            className={`club-tab${view === VIEWS.PLAYERS ? ' is-active' : ''}`}
            type="button"
            role="tab"
            aria-selected={view === VIEWS.PLAYERS}
            aria-controls="club-tabpanel"
            onClick={() => updateFilters({ view: VIEWS.PLAYERS })}
          >
            <Users size={16} aria-hidden="true" /> {t('common.players')}
          </button>
          <button
            className={`club-tab${view === VIEWS.MATCHES ? ' is-active' : ''}`}
            type="button"
            role="tab"
            aria-selected={view === VIEWS.MATCHES}
            aria-controls="club-tabpanel"
            onClick={() => updateFilters({ view: VIEWS.MATCHES })}
          >
            <Swords size={16} aria-hidden="true" /> {t('common.matches')}
          </button>
        </div>
        <div className="club-filters">
          <label className="club-filter">
            <span>{t('common.source')}</span>
            <select
              value={allSourcesSelected ? ALL_SOURCES : sourceFilter}
              onChange={(event) => updateFilters({
                source: event.target.value,
                season: ALL_SEASONS,
                competition: '',
              })}
            >
              <option value={ALL_SOURCES}>{t('detail.allSources')}</option>
              {sources.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
          <label className="club-filter">
            <span>{t('common.season')}</span>
            <select
              value={allSeasonsSelected ? ALL_SEASONS : season}
              onChange={(event) => {
                const nextSeason = event.target.value
                const nextCompetition = sourceCompetitions.some(
                  (item) => item.season === nextSeason && item.name === competition,
                ) || nextSeason === ALL_SEASONS ? competition : ''
                updateFilters({ season: nextSeason, competition: nextCompetition })
              }}
            >
              <option value={ALL_SEASONS}>{t('detail.allSeasons')}</option>
              {availableSeasons.length === 0 ? <option value="">{t('detail.noSeasons')}</option> : null}
              {availableSeasons.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
          <label className="club-filter">
            <span>{t('common.competition')}</span>
            <select
              value={competition}
              onChange={(event) => updateFilters({ competition: event.target.value })}
            >
              <option value="">{t('detail.allCompetitions')}</option>
              {availableCompetitions.map((option) => (
                <option key={`${option.season}-${option.name}`} value={option.name}>{option.name}</option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div id="club-tabpanel" role="tabpanel" aria-label={view === VIEWS.PLAYERS ? t('common.players') : t('common.matches')}>
        {view === VIEWS.PLAYERS
          ? <PlayersPanel players={players} t={t} />
          : <CompetitionsPanel club={club} competitions={filteredCompetitions} returnSearch={searchParams.toString()} t={t} />}
      </div>
    </section>
  )
}

function PlayersPanel({ players, t }) {
  return (
    <section className="club-detail-section" aria-labelledby="club-players-title">
      <h2 id="club-players-title">{t('common.players')}</h2>
      {players.length === 0 ? (
        <p className="club-empty card">{t('detail.registeredPlayersEmpty')}</p>
      ) : (
        <ul className="club-player-list" aria-label={t('detail.clubPlayers')}>
          {players.map((player) => (
            <li key={player.playerSeasonId} className="club-player-card card">
              <strong>{player.playerName ?? player.registrationName}</strong>
              <span>{player.registrationName} · {t('common.season')}: {player.season} · {t('common.license')}: {player.license}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function CompetitionsPanel({ club, competitions, returnSearch, t }) {
  return (
    <section className="club-detail-section" aria-labelledby="club-competitions-title">
      <h2 id="club-competitions-title">{t('common.competitions')}</h2>
      {competitions.length === 0 ? (
        <p className="club-empty card">{t('detail.competitionsEmpty')}</p>
      ) : (
        <ul className="club-competition-list" aria-label={t('detail.clubCompetitions')}>
          {competitions.map((competition) => (
            <li key={`${competition.name}-${competition.season}`}>
              <Link
                className="club-competition-card card"
                to={routePaths.clubCompetitionDetails(
                  club.id,
                  competition.season,
                  competition.name,
                  returnSearch,
                )}
              >
                <span className="club-competition-heading">
                  <strong>{competition.name}</strong>
                  <span>{competition.season}</span>
                </span>
                <span className="club-competition-summary">
                  {t('detail.matchesAvailable', { count: competition.matchCount })} · {competition.resultTotals.wins ?? 0}V /{' '}
                  {competition.resultTotals.draws ?? 0}E / {competition.resultTotals.losses ?? 0}D
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ClubDetailPage
