import { BarChart3, ChevronDown, ChevronRight, Edit3, LayoutDashboard, Swords, Users } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails, useClubMatches } from '../hooks/useClubs.js'
import { groupMatchesHierarchy } from '../utils/clubMatches.js'
import { scopePlayerResultsToCompetition } from '../utils/clubSummary.js'
import ProgressBar from '../components/ui/ProgressBar.jsx'
import ClubSummaryPanel from './ClubSummaryPanel.jsx'
import ClubStatsPanel from './ClubStatsPanel.jsx'
import { useTranslation } from 'react-i18next'

const VIEWS = {
  SUMMARY: 'summary',
  STATS: 'stats',
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
  const view = Object.values(VIEWS).includes(requestedView) ? requestedView : VIEWS.SUMMARY
  const successMessage = searchParams.get('message') || location.state?.successMessage

  if (loading) {
    return (
      <div className="club-state card" role="status" aria-live="polite">
        <p>{t('detail.loadingClub')}</p>
        <ProgressBar value={null} label={t('detail.loadingClub')} />
      </div>
    )
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
  const trendCompetitions = sourceCompetitions.filter((item) => !competition || item.name === competition)
  const players = scopePlayerResultsToCompetition(
    sourcePlayers.filter((player) => (
      (!season || player.season === season)
        && (!competition || player.competitions.includes(competition))
    )),
    competition,
  )

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

  const viewLabels = {
    [VIEWS.SUMMARY]: t('detail.summaryTab'),
    [VIEWS.STATS]: t('detail.statsTab'),
    [VIEWS.PLAYERS]: t('common.players'),
    [VIEWS.MATCHES]: t('common.matches'),
  }

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
            className={`club-tab${view === VIEWS.SUMMARY ? ' is-active' : ''}`}
            type="button"
            role="tab"
            aria-selected={view === VIEWS.SUMMARY}
            aria-controls="club-tabpanel"
            onClick={() => updateFilters({ view: VIEWS.SUMMARY })}
          >
            <LayoutDashboard size={16} aria-hidden="true" /> {t('detail.summaryTab')}
          </button>
          <button
            className={`club-tab${view === VIEWS.STATS ? ' is-active' : ''}`}
            type="button"
            role="tab"
            aria-selected={view === VIEWS.STATS}
            aria-controls="club-tabpanel"
            onClick={() => updateFilters({ view: VIEWS.STATS })}
          >
            <BarChart3 size={16} aria-hidden="true" /> {t('detail.statsTab')}
          </button>
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

      <div id="club-tabpanel" role="tabpanel" aria-label={viewLabels[view]}>
        {view === VIEWS.SUMMARY ? (
          <ClubSummaryPanel
            club={club}
            competitions={filteredCompetitions}
            players={players}
            season={season}
            returnSearch={searchParams.toString()}
            onSeeMatches={() => updateFilters({ view: VIEWS.MATCHES })}
            onSeePlayers={() => updateFilters({ view: VIEWS.PLAYERS })}
            t={t}
          />
        ) : view === VIEWS.STATS ? (
          <ClubStatsPanel
            competitions={filteredCompetitions}
            seasons={club.seasons ?? []}
            trendCompetitions={trendCompetitions}
            season={season}
            t={t}
          />
        ) : view === VIEWS.PLAYERS ? (
          <PlayersPanel players={players} t={t} />
        ) : (
          <MatchesPanel
            club={club}
            competitions={filteredCompetitions}
            returnSearch={searchParams.toString()}
            sourceFilter={sourceFilter}
            season={season}
            competition={competition}
            t={t}
          />
        )}
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
            <li key={player.playerSeasonId}>
              {player.canonicalPlayerId ? (
                <Link
                  className="club-player-card card"
                  to={routePaths.playerDetails(
                    player.canonicalPlayerId,
                    `source=${encodeURIComponent(player.source)}&season=${encodeURIComponent(player.season)}`,
                  )}
                >
                  <span>
                    <strong>{player.playerName ?? player.registrationName}</strong>
                    <span>{player.registrationName} · {t('common.season')}: {player.season} · {t('common.license')}: {player.license}</span>
                  </span>
                  <span aria-hidden="true">→</span>
                </Link>
              ) : (
                <div className="club-player-card card">
                  <strong>{player.playerName ?? player.registrationName}</strong>
                  <span>{player.registrationName} · {t('common.season')}: {player.season} · {t('common.license')}: {player.license}</span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function MatchesPanel({ club, competitions, returnSearch, sourceFilter, season, competition, t }) {
  const competitionsWithSource = useMemo(
    () => competitions.map((item) => ({ ...item, source: item.source ?? club.source })),
    [competitions, club.source],
  )
  const { data: matchGroups, loading, error, retry } = useClubMatches(club.id, competitionsWithSource)
  const [expanded, setExpanded] = useState(() => new Set())

  function toggle(key) {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  if (competitions.length === 0) {
    return (
      <section className="club-detail-section" aria-labelledby="club-matches-title">
        <h2 id="club-matches-title">{t('common.matches')}</h2>
        <p className="club-empty card">{t('detail.clubMatchesEmpty')}</p>
      </section>
    )
  }

  if (loading) {
    return (
      <div className="club-state card" role="status" aria-live="polite">
        <p>{t('detail.loadingClub')}</p>
        <ProgressBar value={null} label={t('detail.loadingClub')} />
      </div>
    )
  }

  if (error || !matchGroups) {
    return (
      <section className="club-detail-section" aria-labelledby="club-matches-title">
        <h2 id="club-matches-title">{t('common.matches')}</h2>
        <p className="club-empty card" role="alert">{t('detail.clubMatchesLoadError')}</p>
        <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
      </section>
    )
  }

  const omitLevels = new Set()
  if (sourceFilter) omitLevels.add('source')
  if (season) omitLevels.add('season')
  if (competition) omitLevels.add('competition')

  const hierarchy = groupMatchesHierarchy(matchGroups, { teams: club.teams, omitLevels })
  const ctx = { club, returnSearch, season, competition, omitLevels, expanded, toggle, t }

  return (
    <section className="club-detail-section" aria-labelledby="club-matches-title">
      <h2 id="club-matches-title">{t('common.matches')}</h2>
      <ul className="club-match-hierarchy" aria-label={t('common.matches')}>
        {renderSourceLevel(hierarchy, ctx)}
      </ul>
    </section>
  )
}

function countMatches(node) {
  if (node.matches) return node.matches.length
  if (node.teams) return node.teams.reduce((sum, teamNode) => sum + countMatches(teamNode), 0)
  if (node.competitions) return node.competitions.reduce((sum, compNode) => sum + countMatches(compNode), 0)
  if (node.seasons) return node.seasons.reduce((sum, seasonNode) => sum + countMatches(seasonNode), 0)
  return 0
}

function renderSourceLevel(hierarchy, ctx) {
  return hierarchy.flatMap((sourceNode) => {
    const path = sourceNode.source
    const children = renderSeasonLevel(sourceNode, path, ctx)

    if (ctx.omitLevels.has('source')) {
      return children
    }

    return (
      <li key={path} className="club-match-hierarchy-item">
        <HierarchyToggle
          level={1}
          label={sourceNode.source}
          count={countMatches(sourceNode)}
          expanded={ctx.expanded.has(path)}
          onToggle={() => ctx.toggle(path)}
          t={ctx.t}
        />
        {ctx.expanded.has(path) ? <ul className="club-match-hierarchy-list">{children}</ul> : null}
      </li>
    )
  })
}

function renderSeasonLevel(sourceNode, path, ctx) {
  if (!sourceNode.seasons) {
    return renderCompetitionLevel(sourceNode, `${path}::${ctx.season}`, ctx.season, ctx)
  }

  return sourceNode.seasons.flatMap((seasonNode) => {
    const seasonPath = `${path}::${seasonNode.season}`
    const children = renderCompetitionLevel(seasonNode, seasonPath, seasonNode.season, ctx)

    return (
      <li key={seasonPath} className="club-match-hierarchy-item">
        <HierarchyToggle
          level={2}
          label={seasonNode.season}
          count={countMatches(seasonNode)}
          expanded={ctx.expanded.has(seasonPath)}
          onToggle={() => ctx.toggle(seasonPath)}
          t={ctx.t}
        />
        {ctx.expanded.has(seasonPath) ? <ul className="club-match-hierarchy-list">{children}</ul> : null}
      </li>
    )
  })
}

function renderCompetitionLevel(node, path, seasonValue, ctx) {
  if (!node.competitions) {
    const competitionPath = `${path}::${ctx.competition}`
    return [
      <li key={competitionPath} className="club-match-hierarchy-item">
        <CompetitionBody
          club={ctx.club}
          season={seasonValue}
          competitionName={ctx.competition}
          teams={node.teams}
          returnSearch={ctx.returnSearch}
          keyPrefix={competitionPath}
          expanded={ctx.expanded}
          toggle={ctx.toggle}
          t={ctx.t}
        />
      </li>,
    ]
  }

  return node.competitions.map((compNode) => {
    const competitionPath = `${path}::${compNode.competition}`
    return (
      <li key={competitionPath} className="club-match-hierarchy-item">
        <HierarchyToggle
          level={3}
          label={compNode.competition}
          count={countMatches(compNode)}
          expanded={ctx.expanded.has(competitionPath)}
          onToggle={() => ctx.toggle(competitionPath)}
          t={ctx.t}
        />
        {ctx.expanded.has(competitionPath) ? (
          <CompetitionBody
            club={ctx.club}
            season={seasonValue}
            competitionName={compNode.competition}
            teams={compNode.teams}
            returnSearch={ctx.returnSearch}
            keyPrefix={competitionPath}
            expanded={ctx.expanded}
            toggle={ctx.toggle}
            t={ctx.t}
          />
        ) : null}
      </li>
    )
  })
}

function CompetitionBody({ club, season, competitionName, teams, returnSearch, keyPrefix, expanded, toggle, t }) {
  return (
    <div className="club-match-group-body">
      <Link
        className="secondary-button"
        to={routePaths.clubCompetitionDetails(club.id, season, competitionName, returnSearch)}
      >
        {t('detail.viewCompetition')}
      </Link>
      {teams.length === 0 ? (
        <p className="club-empty card">{t('detail.competitionEmpty')}</p>
      ) : (
        <ul className="club-match-hierarchy-list">
          {teams.map((teamNode) => {
            const teamPath = `${keyPrefix}::${teamNode.team ?? '__unknown__'}`
            return (
              <li key={teamPath} className="club-match-hierarchy-item">
                <HierarchyToggle
                  level={4}
                  label={teamNode.team ?? t('detail.unknownTeam')}
                  count={teamNode.matches.length}
                  expanded={expanded.has(teamPath)}
                  onToggle={() => toggle(teamPath)}
                  t={t}
                />
                {expanded.has(teamPath) ? (
                  <ul className="club-match-list" aria-label={t('detail.competitionMatchesLabel')}>
                    {teamNode.matches.map((match) => (
                      <li key={match.id}>
                        <Link
                          className="club-match-card card"
                          to={routePaths.matchSummary(match.id, returnSearch)}
                        >
                          <div>
                            <strong>{match.homeTeam} — {match.awayTeam}</strong>
                            <span>
                              {t('detail.round', { round: match.round })}
                              {match.venue ? ` · ${match.venue}` : ''}
                            </span>
                          </div>
                          <div className={`club-match-result is-${match.result}`}>
                            <strong>
                              {match.homeGamesWon == null || match.awayGamesWon == null
                                ? t('detail.pendingResult')
                                : `${match.homeGamesWon} — ${match.awayGamesWon}`}
                            </strong>
                            <span>
                              {match.result === 'win'
                                ? t('detail.win')
                                : match.result === 'loss' ? t('detail.loss') : t('detail.draw')}
                            </span>
                          </div>
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

function HierarchyToggle({ level, label, count, expanded, onToggle, t }) {
  return (
    <button
      type="button"
      className={`club-match-hierarchy-toggle is-level-${level}`}
      aria-expanded={expanded}
      onClick={onToggle}
    >
      {expanded ? <ChevronDown size={16} aria-hidden="true" /> : <ChevronRight size={16} aria-hidden="true" />}
      {' '}<strong>{label}</strong>
      {' '}<span className="club-match-hierarchy-count">{t('detail.matchesAvailable', { count })}</span>
    </button>
  )
}

export default ClubDetailPage
