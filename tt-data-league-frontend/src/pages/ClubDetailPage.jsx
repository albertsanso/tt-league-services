import { BarChart3, ChevronDown, ChevronRight, Edit3, LayoutDashboard, Search, Swords, Users } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails, useClubMatches } from '../hooks/useClubs.js'
import { groupMatchesHierarchy } from '../utils/clubMatches.js'
import {
  aggregateRosterByCanonicalPlayer,
  computeHomeAwaySplit,
  computeOverallRecord,
  countPendingMatches,
  getCurrentStreak,
  getFormGuide,
  getLatestSeasonPlayer,
  getMostActivePlayers,
  getNotableMatches,
  getRosterByCompetition,
  scopePlayerResultsToCompetition,
} from '../utils/clubSummary.js'
import { matchesQuery } from '../utils/textSearch.js'
import ProgressBar from '../components/ui/ProgressBar.jsx'
import ClubSummaryPanel, { MatchRow, StatTile } from './ClubSummaryPanel.jsx'
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
  const rosterPlayers = sourcePlayers.filter((player) => (
    !competition || player.competitions.includes(competition)
  ))
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
          <PlayersPanel players={rosterPlayers} t={t} />
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

function MostActiveRow({ player, t }) {
  return (
    <li>
      <Link
        className="mini-list-item card"
        to={routePaths.playerDetails(
          player.canonicalPlayerId,
          `source=${encodeURIComponent(player.source)}&season=${ALL_SEASONS}`,
        )}
      >
        <span className="mini-list-body">
          <strong>{player.playerName}</strong>
          <span className="mini-list-meta">
            {t('detail.playersMostActiveSub', { seasons: player.seasons.length })}
          </span>
        </span>
        <span className="mini-list-stat">
          {t('detail.summaryPlayerMatchCount', { count: player.matchCount })}
        </span>
      </Link>
    </li>
  )
}

const COMPETITION_ROWS_LIMIT = 5

function PlayersByCompetitionCard({ byCompetition, totalByCompetition, t }) {
  const [expanded, setExpanded] = useState(false)
  const visibleCompetitions = expanded ? byCompetition : byCompetition.slice(0, COMPETITION_ROWS_LIMIT)
  const hiddenCount = byCompetition.length - visibleCompetitions.length

  return (
    <div className="card-block card">
      <div className="card-block-header">
        <h3>{t('detail.playersByCompetitionTitle')}</h3>
      </div>
      {byCompetition.length === 0 ? null : (
        <>
          <ul className="comp-row-list">
            {visibleCompetitions.map((item) => (
              <li key={item.competition} className="comp-row">
                <span className="comp-row-name"><strong>{item.competition}</strong></span>
                <span className="comp-row-counts">{item.playerCount}</span>
                <span className="record-bar comp-row-bar" role="presentation">
                  <span
                    className="record-bar-segment is-fill"
                    style={{ flexBasis: `${(item.playerCount / totalByCompetition) * 100}%` }}
                  />
                </span>
                <span className="comp-row-rate">
                  {Math.round((item.playerCount / totalByCompetition) * 100)}%
                </span>
              </li>
            ))}
          </ul>
          {hiddenCount > 0 ? (
            <button type="button" className="link-button" onClick={() => setExpanded(true)}>
              {t('detail.seeAll')} →
            </button>
          ) : null}
        </>
      )}
    </div>
  )
}

function PlayersSummaryStrip({ rosterAggregates, byCompetition, mostActive, latestSeasonPlayer, t }) {
  const totalByCompetition = byCompetition.reduce((sum, item) => sum + item.playerCount, 0) || 1
  const sourceCount = new Set(rosterAggregates.map((player) => player.source)).size

  return (
    <div className="players-summary-strip">
      <div className="stat-grid">
        <StatTile
          label={t('common.players')}
          value={rosterAggregates.length}
          subLabel={t('detail.playersSourcesCount', { count: sourceCount })}
        />
        <StatTile
          label={t('common.competitions')}
          value={byCompetition.length}
          subLabel={byCompetition[0]
            ? `${byCompetition[0].competition} · ${t('detail.playersMostRepresented', { count: byCompetition[0].playerCount })}`
            : '—'}
        />
        <StatTile
          label={t('detail.playersMostActiveTitle')}
          value={mostActive[0]?.playerName ?? '—'}
          subLabel={t('detail.summaryPlayerMatchCount', { count: mostActive[0]?.matchCount ?? 0 })}
        />
        <StatTile
          label={t('detail.playersLatestSeason')}
          value={latestSeasonPlayer?.seasons[latestSeasonPlayer.seasons.length - 1] ?? '—'}
          subLabel={latestSeasonPlayer?.playerName ?? ''}
        />
      </div>

      <div className="summary-grid">
        <PlayersByCompetitionCard byCompetition={byCompetition} totalByCompetition={totalByCompetition} t={t} />

        <div className="card-block card">
          <div className="card-block-header">
            <h3>{t('detail.playersMostActiveTitle')}</h3>
          </div>
          {mostActive.length === 0 ? (
            <p className="club-empty card">{t('detail.registeredPlayersEmpty')}</p>
          ) : (
            <ul className="mini-list">
              {mostActive.map((player) => (
                <MostActiveRow key={player.canonicalPlayerId} player={player} t={t} />
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}

function PlayersPanel({ players, t }) {
  const [nameQuery, setNameQuery] = useState('')
  const rosterAggregates = useMemo(() => aggregateRosterByCanonicalPlayer(players), [players])
  const byCompetition = useMemo(() => getRosterByCompetition(rosterAggregates), [rosterAggregates])
  const mostActive = useMemo(() => getMostActivePlayers(rosterAggregates, 4), [rosterAggregates])
  const latestSeasonPlayer = useMemo(() => getLatestSeasonPlayer(rosterAggregates), [rosterAggregates])
  const filteredPlayers = useMemo(
    () => rosterAggregates.filter((player) => matchesQuery(player.playerName, nameQuery)),
    [rosterAggregates, nameQuery],
  )

  return (
    <section className="club-detail-section" aria-labelledby="club-players-title">
      <h2 id="club-players-title">{t('common.players')}</h2>
      {rosterAggregates.length === 0 ? (
        <p className="club-empty card">{t('detail.registeredPlayersEmpty')}</p>
      ) : (
        <>
          <PlayersSummaryStrip
            rosterAggregates={rosterAggregates}
            byCompetition={byCompetition}
            mostActive={mostActive}
            latestSeasonPlayer={latestSeasonPlayer}
            t={t}
          />
          <label className="sr-only" htmlFor="club-players-search">{t('detail.playersSearchLabel')}</label>
          <div className="club-search-input-wrap">
            <Search size={17} aria-hidden="true" />
            <input
              id="club-players-search"
              className="club-search-input"
              type="search"
              value={nameQuery}
              onChange={(event) => setNameQuery(event.target.value)}
              placeholder={t('detail.playersSearchPlaceholder')}
              autoComplete="off"
              aria-describedby="club-players-search-summary"
            />
          </div>
          <p id="club-players-search-summary" className="search-summary" aria-live="polite">
            {t('detail.playersShownCount', { shown: filteredPlayers.length, total: rosterAggregates.length })}
          </p>
          {filteredPlayers.length === 0 ? (
            <p className="club-empty card">{t('detail.playersSearchEmpty', { query: nameQuery })}</p>
          ) : (
            <ul className="club-player-list" aria-label={t('detail.clubPlayers')}>
              {filteredPlayers.map((player) => (
                <li key={player.canonicalPlayerId}>
                  <Link
                    className="club-player-card card"
                    to={routePaths.playerDetails(
                      player.canonicalPlayerId,
                      `source=${encodeURIComponent(player.source)}&season=${ALL_SEASONS}`,
                    )}
                  >
                    <strong>{player.playerName}</strong>
                    <span aria-hidden="true">→</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </>
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
  const taggedMatches = matchGroups.flatMap((group) => group.matches.map((match) => ({
    ...match,
    competition: group.competition,
    season: group.season,
    source: group.source,
  })))

  return (
    <section className="club-detail-section" aria-labelledby="club-matches-title">
      <h2 id="club-matches-title">{t('common.matches')}</h2>
      {taggedMatches.length > 0 ? (
        <MatchesSummaryStrip
          matches={taggedMatches}
          competitions={competitions}
          teams={club.teams}
          returnSearch={returnSearch}
          t={t}
        />
      ) : null}
      <ul className="club-match-hierarchy" aria-label={t('common.matches')}>
        {renderSourceLevel(hierarchy, ctx)}
      </ul>
    </section>
  )
}

function MatchesSummaryStrip({ matches, competitions, teams, returnSearch, t }) {
  const record = useMemo(() => computeOverallRecord(competitions), [competitions])
  const homeAway = useMemo(() => computeHomeAwaySplit(matches, teams), [matches, teams])
  const pending = useMemo(() => countPendingMatches(matches), [matches])
  const formGuide = useMemo(() => getFormGuide(matches), [matches])
  const streak = useMemo(() => getCurrentStreak(matches), [matches])
  const notable = useMemo(() => getNotableMatches(matches), [matches])
  const resultLabel = (result) => (
    result === 'win' ? t('detail.win') : result === 'loss' ? t('detail.loss') : t('detail.draw')
  )

  return (
    <div className="matches-summary-strip">
      <div className="stat-grid">
        <StatTile
          label={t('common.playedMatches')}
          value={record.matchCount}
          subLabel={`${record.wins}${t('detail.winsAbbrev')} · ${record.draws}${t('detail.drawsAbbrev')} · ${record.losses}${t('detail.lossesAbbrev')}`}
        />
        <StatTile
          label={t('common.winPercentage')}
          value={`${record.winRate}%`}
          subLabel={`${record.wins}${t('detail.winsAbbrev')} · ${record.draws}${t('detail.drawsAbbrev')} · ${record.losses}${t('detail.lossesAbbrev')}`}
        />
        <StatTile
          label={t('detail.matchesHomeAwayTile')}
          value={`${homeAway.home.winRate}% / ${homeAway.away.winRate}%`}
          subLabel={t('common.winPercentage')}
        />
        <StatTile
          label={t('detail.matchesPending')}
          value={pending}
          subLabel={t('detail.pendingResult')}
        />
      </div>

      <p className="matches-form-guide">
        {t('detail.matchesFormGuide')}:{' '}
        {formGuide.map((match) => (
          <span
            key={match.id}
            className={`match-row-result is-${match.result} form-guide-chip`}
            title={resultLabel(match.result)}
          >
            {resultLabel(match.result)[0]}
          </span>
        ))}
        {streak ? (
          <span className="matches-current-streak">
            {' '}({t('detail.matchesCurrentStreak', { result: resultLabel(streak.result), count: streak.count })})
          </span>
        ) : null}
      </p>

      {notable.closest || notable.biggestWin || notable.biggestLoss ? (
        <div className="card-block card">
          <div className="card-block-header">
            <h3>{t('detail.matchesNotableTitle')}</h3>
          </div>
          <ul className="match-row-list">
            {notable.closest ? (
              <MatchRow
                key="closest"
                match={{ ...notable.closest, competition: `${t('detail.matchesClosest')} · ${notable.closest.competition}` }}
                returnSearch={returnSearch}
                t={t}
              />
            ) : null}
            {notable.biggestWin ? (
              <MatchRow
                key="biggestWin"
                match={{ ...notable.biggestWin, competition: `${t('detail.matchesBiggestWin')} · ${notable.biggestWin.competition}` }}
                returnSearch={returnSearch}
                t={t}
              />
            ) : null}
            {notable.biggestLoss ? (
              <MatchRow
                key="biggestLoss"
                match={{ ...notable.biggestLoss, competition: `${t('detail.matchesBiggestLoss')} · ${notable.biggestLoss.competition}` }}
                returnSearch={returnSearch}
                t={t}
              />
            ) : null}
          </ul>
        </div>
      ) : null}
    </div>
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
