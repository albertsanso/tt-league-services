import { ArrowLeft } from 'lucide-react'
import { Fragment, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerDetails } from '../hooks/usePlayers.js'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n/index.js'
import { computeTrendNote } from '../utils/matchSummary.js'

const ALL = 'all'
const VIEWS = {
  STATISTICS: 'statistics',
  MATCHES: 'matches',
  OPPONENTS: 'opponents',
}

function compareSeasons(left, right) {
  return String(right.season ?? '').localeCompare(String(left.season ?? ''), 'ca', { numeric: true })
}

function compareSeasonsAscending(left, right) {
  return String(left.season ?? '').localeCompare(String(right.season ?? ''), 'ca', { numeric: true })
}
const OPPONENT_FILTERS = {
  ALL: 'all',
  FAVORABLE: 'favorable',
  HARD: 'hard',
  PROBLEM: 'problem',
}
const OPPONENT_SORTS = {
  DEFAULT: 'default',
  WIN_PERCENTAGE: 'winPercentage',
  MATCHES: 'matches',
  LAST_PLAYED: 'lastPlayed',
  CLOSENESS: 'closeness',
}
const RECENT_FORM_SIZE = 5
const PERCENTAGE_TICKS = [0, 25, 50, 75, 100]
const MATCHES_MARKER_SIZE = 6
const WINS_MARKER_SIZE = 6
const unique = (values) => [...new Set(values.filter(Boolean))].sort()
const MATCHES_PER_PAGE = 10
const SPECTRUM_TIERS = ['strong-win', 'win', 'close-win', 'draw', 'close-loss', 'loss', 'strong-loss']

function PlayerDetailPage() {
  const { playerId } = useParams()
  const [params, setParams] = useSearchParams()
  const sourceFilter = params.get('source') === ALL ? '' : params.get('source') || ''
  const seasonFilter = params.get('season') === ALL ? '' : params.get('season') || ''
  const competitionFilter = params.get('competition') === ALL ? '' : params.get('competition') || ''
  const { data, loading, error, retry } = usePlayerDetails(
    playerId, sourceFilter, seasonFilter, competitionFilter,
  )
  const { t } = useTranslation()
  if (loading && !data) return <p className="club-state card" role="status">{t('detail.loadingClub')}</p>
  if (error?.status === 404 || error?.status === 400) {
    return <section className="page-block" role="alert">
      <h1 className="page-title">{t('detail.playerNotFound')}</h1>
      <p className="page-description">{t('detail.playerNotFoundDescription')}</p>
      <Link className="secondary-button" to={routePaths.players()}>{t('detail.backSearch')}</Link>
    </section>
  }
  if (error || !data) {
    return <section className="page-block" role="alert">
      <h1 className="page-title">{t('detail.playerLoadError')}</h1>
      <p className="page-description">
        {error?.status === 401
          ? t('detail.sessionExpiredDetails')
          : t('detail.clubLoadDescription')}
      </p>
      <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
    </section>
  }
  return (
    <>
      {loading ? <p className="visually-hidden" role="status" aria-live="polite">{t('detail.updatingPlayer')}</p> : null}
      <PlayerDetailContent data={data} params={params} setParams={setParams} t={t} />
    </>
  )
}

function PlayerDetailContent({ data, params, setParams, t }) {
  const requestedView = params.get('view')
  const view = Object.values(VIEWS).includes(requestedView) ? requestedView : VIEWS.STATISTICS
  const sources = unique([
    ...data.federatedPlayers.map((item) => item.source),
    ...data.registrations.map((item) => item.source),
    ...data.competitions.map((item) => item.source),
    ...data.matches.map((item) => item.source),
  ]).filter((item) => item !== '—')
  const selectedSource = params.get('source')
  const source = selectedSource && sources.includes(selectedSource) ? selectedSource : ''
  const registrations = data.registrations.filter((item) => !source || item.source === source)
  const seasons = unique([
    ...registrations.map((item) => item.season),
    ...data.competitions.filter((item) => !source || item.source === source).map((item) => item.season),
    ...data.matches.map((item) => item.season),
    ...data.statistics.filter((item) => !source || item.source === source).map((item) => item.season),
  ]).filter((item) => item !== '—')
  const seasonLabelWidth = Math.max(
    t('detail.allSeasons').length,
    ...seasons.map((item) => item.length),
  )
  const selectedSeason = params.get('season')
  const season = selectedSeason === ALL ? '' : seasons.includes(selectedSeason) ? selectedSeason : ''
  const availableCompetitions = unique(data.competitions
    .filter((item) => (!source || item.source === source) && (!season || item.season === season))
    .map((item) => item.name))
  const selectedCompetition = params.get('competition')
  const competition = availableCompetitions.includes(selectedCompetition) ? selectedCompetition : ''
  const matches = data.matches
  const statistics = data.statistics

  useEffect(() => {
    if (requestedView !== view) {
      const next = new URLSearchParams(params)
      next.set('view', view)
      setParams(next, { replace: true })
    }
  }, [params, requestedView, setParams, view])

  function update(key, value) {
    const next = new URLSearchParams(params)
    next.set(key, value || ALL)
    if (key === 'source') {
      next.set('season', ALL)
      next.delete('competition')
    }
    if (key === 'season') next.delete('competition')
    setParams(next)
  }

  return (
    <section className="page-block player-detail-page" aria-labelledby="player-detail-title">
      <Link className="back-link" to={routePaths.players()}> <ArrowLeft size={16} aria-hidden="true" /> {t('detail.backSearch')}</Link>
      <div className="club-detail-header">
        <div>
          <p className="section-label">{t('detail.identityCanonical')}</p>
          <h1 id="player-detail-title" className="page-title">{data.name}</h1>
          <p className="club-source">{t('detail.uuid', { id: data.id })}</p>
        </div>
      </div>
      <div className="player-detail-controls">
      <div className="club-filters">
        <fieldset className="club-filter season-slider" style={{ '--season-label-width': `${seasonLabelWidth}ch` }}><legend>{t('common.season')}</legend>
          <output htmlFor="player-season">{season || t('detail.allSeasons')}</output>
          <input id="player-season" type="range" min="0" max={Math.max(seasons.length, 0)} step="1"
            value={season ? seasons.indexOf(season) : seasons.length}
            onChange={(event) => update('season', seasons[Number(event.target.value)] ?? ALL)}
            disabled={seasons.length === 0}
            aria-label={t('detail.selectSeason')} />
          {seasons.length > 0 ? <div className="season-marks" aria-hidden="true">
            {seasons.map((item) => <span key={item}>{item}</span>)}
            <span>{t('common.all')}</span>
          </div> : null}
          <button className="filter-all-button" type="button" onClick={() => update('season', ALL)}>{t('detail.allSeasons')}</button>
        </fieldset>
        <fieldset className="club-filter source-options"><legend>{t('common.source')}</legend>
          <label><input type="radio" name="player-source" checked={!source} onChange={() => update('source', ALL)} /> {t('detail.allSources')}</label>
          {sources.map((item) => <label key={item}><input type="radio" name="player-source" value={item} checked={source === item} onChange={() => update('source', item)} /> {item}</label>)}
        </fieldset>
        <label className="club-filter player-competition-filter"><span>{t('common.competition')}</span><select value={competition} onChange={(event) => update('competition', event.target.value)}>
          <option value="">{t('detail.allCompetitions')}</option>{availableCompetitions.map((item) => <option key={item} value={item}>{item}</option>)}
        </select></label>
      </div>
      <div className="club-tabs player-tabs" role="tablist" aria-label={t('detail.playerViews')}>
        <button id="player-statistics-tab" className={`club-tab${view === VIEWS.STATISTICS ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.STATISTICS} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.STATISTICS)} onKeyDown={(event) => activateTab(event, VIEWS.STATISTICS, update)}>{t('overview.statistics')}</button>
        <button id="player-matches-tab" className={`club-tab${view === VIEWS.MATCHES ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.MATCHES} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.MATCHES)} onKeyDown={(event) => activateTab(event, VIEWS.MATCHES, update)}>{t('common.matches')}</button>
        <button id="player-opponents-tab" className={`club-tab${view === VIEWS.OPPONENTS ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.OPPONENTS} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.OPPONENTS)} onKeyDown={(event) => activateTab(event, VIEWS.OPPONENTS, update)}>{t('detail.opponentAnalysis')}</button>
      </div>
      </div>
      <div id="player-tabpanel" role="tabpanel" aria-labelledby={`player-${view}-tab`}>
        {view === VIEWS.STATISTICS ? <HistorySection statistics={statistics} competition={competition} matches={matches} t={t} /> : null}
        {view === VIEWS.MATCHES ? <MatchHistoryPanel key={`${source}-${season}-${competition}`} matches={matches} returnSearch={params} t={t} /> : null}
        {view === VIEWS.OPPONENTS ? <OpponentAnalysisPanel matches={matches} params={params} update={update} t={t} /> : null}
      </div>
    </section>
  )
}

function HistorySection({ statistics, competition, matches, t }) {
  const values = [...(competition ? aggregateCompetition(matches, competition) : statistics)].sort(compareSeasons)
  const chartValues = [...values].sort(compareSeasonsAscending)
  const career = matches.length > 0 ? aggregateCareerStatistics(matches) : null
  return <section className="club-detail-section" aria-labelledby="player-history-title">
    <h2 id="player-history-title">{t('detail.statisticsHistory')}</h2>
    {values.length === 0 ? <p className="club-empty card" role="status">{t('detail.statisticsEmpty')}</p> : (
      <>
        {career ? <CareerSummary career={career} t={t} /> : null}
        <div className="history-charts-row">
          <ConnectedScatterPlot values={chartValues} />
          <MatchQualitySpectrumChart matches={matches} t={t} />
        </div>
        <p className="history-legend" aria-label={t('detail.chartLegend')}>
          <span className="legend-matches">{t('common.playedMatches')}</span>
          <span className="legend-wins">{t('common.winPercentage')}</span>
        </p>
        <div className="table-wrap">
          <table className="history-table">
            <caption>{t('detail.historyValues')}</caption>
            <thead><tr><th>{t('common.season')}</th><th>{t('common.playedMatches')}</th><th>{t('common.winPercentage')}</th><th>{t('detail.averageScore')}</th></tr></thead>
            <tbody>{values.map((item, index) => <tr key={`${item.source}-${item.season}-${index}`}><td>{item.season || '—'}</td><td>{item.matchesPlayed}</td><td>{item.winPercentage == null ? '—' : `${item.winPercentage.toFixed(1)}%`}</td><td>{item.averageScore == null ? '—' : item.averageScore.toFixed(1)}</td></tr>)}</tbody>
          </table>
        </div>
      </>
    )}
  </section>
}

function CareerSummary({ career, t }) {
  return <div className="career-summary card" aria-labelledby="career-summary-title">
    <h3 id="career-summary-title">{t('detail.careerSummary')}</h3>
    <dl className="career-summary-stats">
      <div><dt>{t('common.playedMatches')}</dt><dd>{career.matchesPlayed}</dd></div>
      <div><dt>{t('common.winPercentage')}</dt><dd>{formatWinPercentage(career.winPercentage)}</dd></div>
      <div><dt>{t('detail.currentStreakLabel')}</dt><dd>{streakLabel(career.currentStreak, t)}</dd></div>
      <div><dt>{t('detail.longestWinStreak')}</dt><dd>{career.longestWinStreak}</dd></div>
      <div><dt>{t('detail.singlesWinPercentage')}</dt><dd>{formatWinPercentage(career.singlesWinPercentage)}</dd></div>
      <div><dt>{t('detail.doublesWinPercentage')}</dt><dd>{formatWinPercentage(career.doublesWinPercentage)}</dd></div>
      <div><dt>{t('detail.averageSetMargin')}</dt><dd>{career.averageSetMargin == null ? '—' : career.averageSetMargin.toFixed(1)}</dd></div>
    </dl>
  </div>
}

function MatchHistoryPanel({ matches, returnSearch, t }) {
  const [page, setPage] = useState(0)
  const sortedMatches = [...matches].sort(compareMatches)
  const pageCount = Math.ceil(sortedMatches.length / MATCHES_PER_PAGE)
  const visibleMatches = sortedMatches.slice(page * MATCHES_PER_PAGE, (page + 1) * MATCHES_PER_PAGE)

  return <section className="club-detail-section" aria-labelledby="player-matches-title">
    <h2 id="player-matches-title">{t('common.matches')}</h2>
    {matches.length === 0 ? <p className="club-empty card" role="status">{t('detail.competitionEmpty')}</p> : (
      <div className="match-history">
        <ul className="match-card-list" aria-labelledby="player-matches-title">
          {visibleMatches.map((item) => <MatchCard key={item.id} match={item} returnSearch={returnSearch} t={t} />)}
        </ul>
        {pageCount > 1 ? <nav className="pagination" aria-label={t('common.matches')}>
          <button type="button" onClick={() => setPage((current) => current - 1)} disabled={page === 0}>{t('common.previous')}</button>
          <span aria-live="polite">{t('common.pageOf', { page: page + 1, count: pageCount })}</span>
          <button type="button" onClick={() => setPage((current) => current + 1)} disabled={page === pageCount - 1}>{t('common.next')}</button>
        </nav> : null}
      </div>
    )}
  </section>
}

function GameOpponents({ opponents, returnSearch }) {
  return opponents.map((opponent, index) => <Fragment key={opponent.key}>
    {index > 0 ? ', ' : ''}
    {opponent.playerId
      ? <Link to={routePaths.playerDetails(opponent.playerId, returnSearch)} onClick={(event) => event.stopPropagation()}>{opponent.name}</Link>
      : opponent.name}
  </Fragment>)
}

function MatchCard({ match, returnSearch, t }) {
  const games = matchGameRows(match, t)
  return <li>
    <details className="match-card card">
      <summary className="match-card-summary">
        <span className={`match-card-badge match-result-${match.result}`} title={resultLabel(match.result, t)}>
          {resultBadgeLabel(match.result, t)}
        </span>
        <span className="match-card-heading">
          <span className="match-card-title-row">
            <span className="match-card-opponent">{opponentTeamName(match, t)}</span>
            <span className="match-card-score">{scoreLabel(match, t)}</span>
          </span>
          <span className="match-card-competition">
            {match.competition}
            {match.round != null ? ` · ${t('matchesPage.round')} ${match.round}` : ''}
          </span>
          <span className="match-card-chips">
            {match.season ? <span className="chip" title={t('common.season')}>{match.season}</span> : null}
            {match.source ? <span className="chip" title={t('common.source')}>{match.source}</span> : null}
            {match.groupNumber != null ? <span className="chip" title={t('matchesPage.group')}>{match.groupNumber}</span> : null}
            {match.phase ? <span className="chip" title={t('matchesPage.phase')}>{match.phase}</span> : null}
          </span>
        </span>
        <span className="match-card-side">
          <span className="match-card-date">{match.dateTime ? new Date(match.dateTime).toLocaleDateString(i18n.language) : t('common.noData')}</span>
          <Link to={routePaths.matchSummary(match.id, returnSearch)} onClick={(event) => event.stopPropagation()}>
            {t('detail.viewMatch')}
          </Link>
          <span className="match-card-toggle">{t('matchesPage.games')} ({games.length})</span>
        </span>
      </summary>
      <div className="match-card-games" role="list" aria-label={t('matchesPage.games')}>
        {games.map((game) => <div className="match-card-game-row" role="listitem" key={game.id}>
          <span className="match-card-game-type">{game.typeLabel}</span>
          <span className="match-card-game-opponents"><GameOpponents opponents={game.opponents} returnSearch={returnSearch} /></span>
          <span className={`match-card-game-result match-result-${game.result}`}>
            <span className="match-card-game-outcome">{game.resultLabel}</span>
            <span className="match-card-game-score">{game.scoreLabel}</span>
          </span>
        </div>)}
      </div>
    </details>
  </li>
}

function OpponentAnalysisPanel({ matches, params, update, t }) {
  const [search, setSearch] = useState('')
  const requestedFilter = params.get('opponentFilter')
  const filter = Object.values(OPPONENT_FILTERS).includes(requestedFilter) ? requestedFilter : OPPONENT_FILTERS.ALL
  const requestedSort = params.get('opponentSort')
  const sort = Object.values(OPPONENT_SORTS).includes(requestedSort) ? requestedSort : OPPONENT_SORTS.DEFAULT
  const sortComparator = opponentComparator(sort)
  const opponents = new Map()
  matches.forEach((match) => {
    const opponentKeys = new Set()
    const games = match.games ?? []
    const isHome = match.playerTeam === match.homeTeam
    games.forEach((game) => {
      const sets = gameSetsForPlayer(match, game)
      game.opponents.forEach((opponent) => {
        const key = opponentKey(opponent)
        if (opponentKeys.has(key)) return
        opponentKeys.add(key)
        addOpponent(opponents, key, opponent, {
          id: `${match.id}-${game.id}`,
          matchId: match.id,
          dateTime: match.dateTime,
          competition: match.competition,
          season: match.season,
          result: game.result,
          playerSets: sets.playerSets,
          opponentSets: sets.opponentSets,
          gameType: game.type,
          isHome,
        })
      })
    })
    if (match.games == null || match.games.length === 0) {
      const opponent = { name: opponentName(match), available: true }
      const legacyGames = matchGamesForPlayer(match)
      addOpponent(opponents, `legacy-${opponent.name}`, opponent, {
        id: match.id,
        matchId: match.id,
        dateTime: match.dateTime,
        competition: match.competition,
        season: match.season,
        result: match.result,
        playerSets: legacyGames.playerGames,
        opponentSets: legacyGames.opponentGames,
        gameType: null,
        isHome,
      })
    } else if (opponentKeys.size === 0) {
      addOpponent(opponents, `unavailable-${match.id}`, { name: null, available: false }, {
        id: match.id,
        matchId: match.id,
        dateTime: match.dateTime,
        competition: match.competition,
        season: match.season,
        result: match.result,
        playerSets: null,
        opponentSets: null,
        gameType: null,
        isHome,
      })
    }
  })
  const rows = [...opponents.values()].map((opponent) => buildOpponentRow(opponent))
  const overallWinPercentage = winPercentage(matches.reduce((totals, match) => ({
    wins: totals.wins + (match.result === 'win' ? 1 : 0),
    losses: totals.losses + (match.result === 'loss' ? 1 : 0),
  }), { wins: 0, losses: 0 }))
  const categorizedRows = rows.map((opponent) => ({
    ...opponent,
    category: opponentCategory(opponent, overallWinPercentage),
  }))
  const filterCounts = {
    [OPPONENT_FILTERS.ALL]: categorizedRows.length,
    [OPPONENT_FILTERS.FAVORABLE]: categorizedRows.filter((opponent) => opponent.category === 'favorable').length,
    [OPPONENT_FILTERS.HARD]: categorizedRows.filter((opponent) => opponent.category === 'hard').length,
    [OPPONENT_FILTERS.PROBLEM]: categorizedRows.filter((opponent) => opponent.category === 'problem').length,
  }
  const filteredRows = categorizedRows
    .filter((opponent) => filter === OPPONENT_FILTERS.ALL || opponent.category === filter)
    .filter((opponent) => opponent.name.toLocaleLowerCase('ca-ES').includes(search.toLocaleLowerCase('ca-ES')))
    .sort(sortComparator ?? compareCategorizedOpponents)

  return <section className="club-detail-section" aria-labelledby="player-opponents-title">
    <h2 id="player-opponents-title">{t('detail.opponentAnalysis')}</h2>
    <div className="opponent-filter-chips" role="group" aria-label={t('detail.opponentFilterGroupLabel')}>
      <OpponentFilterChip active={filter === OPPONENT_FILTERS.ALL} label={t('detail.opponentFilterAll')} count={filterCounts[OPPONENT_FILTERS.ALL]} onClick={() => update('opponentFilter', OPPONENT_FILTERS.ALL)} t={t} />
      <OpponentFilterChip active={filter === OPPONENT_FILTERS.FAVORABLE} label={t('detail.categoryFavorableLabel')} count={filterCounts[OPPONENT_FILTERS.FAVORABLE]} tone="favorable" onClick={() => update('opponentFilter', OPPONENT_FILTERS.FAVORABLE)} t={t} />
      <OpponentFilterChip active={filter === OPPONENT_FILTERS.HARD} label={t('detail.categoryHardLabel')} count={filterCounts[OPPONENT_FILTERS.HARD]} tone="hard" onClick={() => update('opponentFilter', OPPONENT_FILTERS.HARD)} t={t} />
      <OpponentFilterChip active={filter === OPPONENT_FILTERS.PROBLEM} label={t('detail.categoryProblemLabel')} count={filterCounts[OPPONENT_FILTERS.PROBLEM]} tone="problem" onClick={() => update('opponentFilter', OPPONENT_FILTERS.PROBLEM)} t={t} />
    </div>
    <div className="opponent-toolbar">
      <label className="opponent-search">
        <span>{t('detail.opponentSearch')}</span>
        <input className="opponent-search-input" type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
      </label>
      <label className="opponent-sort">
        <span>{t('detail.opponentSort')}</span>
        <select value={sort} onChange={(event) => update('opponentSort', event.target.value)}>
          <option value={OPPONENT_SORTS.DEFAULT}>{t('detail.opponentSortDefault')}</option>
          <option value={OPPONENT_SORTS.WIN_PERCENTAGE}>{t('detail.opponentSortWinPercentage')}</option>
          <option value={OPPONENT_SORTS.MATCHES}>{t('detail.opponentSortMatches')}</option>
          <option value={OPPONENT_SORTS.LAST_PLAYED}>{t('detail.opponentSortLastPlayed')}</option>
          <option value={OPPONENT_SORTS.CLOSENESS}>{t('detail.opponentSortCloseness')}</option>
        </select>
      </label>
    </div>
    {filteredRows.length === 0 ? <p className="club-empty card" role="status">{t('detail.opponentsEmpty')}</p> : (
      <OpponentTable rows={filteredRows} summaryText={t('detail.opponentListSummary', { count: filteredRows.length })} returnSearch={params} t={t} />
    )}
  </section>
}

function OpponentFilterChip({ active, label, count, tone, onClick, t }) {
  return <button type="button" className={`opponent-filter-chip${tone ? ` opponent-filter-chip--${tone}` : ''}${active ? ' is-active' : ''}`}
    aria-pressed={active} aria-label={t('detail.opponentFilterAriaLabel', { label, count })} onClick={onClick}>
    <span className="opponent-filter-chip-label">{label}</span>
    <span className="opponent-filter-chip-count">{count}</span>
  </button>
}

function OpponentTable({ rows, summaryText, returnSearch, t }) {
  const [expanded, setExpanded] = useState(false)
  const [expandedOpponent, setExpandedOpponent] = useState(null)
  const maxVisible = 3
  const visibleRows = rows.slice(0, maxVisible)
  const hiddenRows = rows.slice(maxVisible)
  const descriptionId = `opponent-table-description-${rows.map((row) => row.key).join('-')}`
  const columnCount = 9
  const toggleExpanded = (key) => setExpandedOpponent((current) => current === key ? null : key)
  const table = (tableRows) => <table className="history-table" aria-describedby={descriptionId}>
      <caption>{t('detail.opponentResults')}</caption>
      <thead><tr><th>{t('common.opponent')}</th><th>{t('common.category')}</th><th>{t('common.playedMatches')}</th><th>{t('common.wins')}</th><th>{t('common.draws')}</th><th>{t('common.losses')}</th><th>{t('common.winPercentage')}</th><th>{t('detail.recentForm')}</th><th>{t('detail.streak')}</th></tr></thead>
      <tbody>{tableRows.map((item) => {
        const isExpanded = expandedOpponent === item.key
        return <Fragment key={item.key}>
          <tr className={isExpanded ? 'opponent-row is-expanded' : 'opponent-row'} tabIndex={0} aria-expanded={isExpanded}
            onClick={() => toggleExpanded(item.key)}
            onKeyDown={(event) => {
              if (event.key !== 'Enter' && event.key !== ' ') return
              event.preventDefault()
              toggleExpanded(item.key)
            }}>
            <td data-label={t('common.opponent')}>{item.name}</td>
            <td data-label={t('common.category')}><OpponentCategoryBadge category={item.category} t={t} /></td>
            <td data-label={t('common.playedMatches')}>{item.matches}</td>
            <td data-label={t('common.wins')}>{item.wins}</td>
            <td data-label={t('common.draws')}>{item.draws}</td>
            <td data-label={t('common.losses')}>{item.losses}</td>
            <td data-label={t('common.winPercentage')}>{formatWinPercentage(item.playerWinPercentage)}</td>
            <td data-label={t('detail.recentForm')}><OpponentFormChips history={item.recentForm} t={t} /></td>
            <td data-label={t('detail.streak')}>{streakLabel(item.streak, t)}</td>
          </tr>
          {isExpanded ? <tr className="opponent-history-row">
            <td colSpan={columnCount}>
              <OpponentHeadToHead item={item} returnSearch={returnSearch} t={t} />
            </td>
          </tr> : null}
        </Fragment>
      })}</tbody>
    </table>
  return <div className="opponent-table">
    <p id={descriptionId} className="visually-hidden">{summaryText}</p>
    <div className="table-wrap">{table(expanded ? rows : visibleRows)}</div>
    {hiddenRows.length > 0 && !expanded ? <div className="opponent-more">
      <button type="button" onClick={() => setExpanded((current) => !current)}>
        {t('detail.showMore', { count: hiddenRows.length })}
      </button>
    </div> : null}
  </div>
}

function OpponentFormChips({ history, t }) {
  if (history.length === 0) return <span className="opponent-form-empty">{t('common.unavailable')}</span>
  return <span className="opponent-form" role="list" aria-label={t('detail.recentForm')}>
    {[...history].reverse().map((entry) => {
      const tier = qualityTier(entry.result, entry.margin)
      const label = qualityLabel(tier, t)
      return <span key={entry.id} role="listitem" className={`opponent-form-chip quality-${tier ?? 'unknown'}`} title={label} aria-label={label}>●</span>
    })}
  </span>
}

function OpponentHeadToHead({ item, returnSearch, t }) {
  const { key, name, sortedHistory: history } = item
  const titleId = `opponent-h2h-title-${sanitizeId(key)}`
  return <div className="opponent-history-detail">
    <h4 id={titleId}>{t('detail.headToHeadTitle', { name: name ?? t('common.unavailable') })}</h4>
    <OpponentInsightsPanel item={item} t={t} />
    {history.length === 0 ? <p className="club-empty card" role="status">{t('detail.headToHeadEmpty')}</p> : (
      <div className="match-card-games opponent-h2h-list" role="list" aria-labelledby={titleId}>
        {history.map((entry) => <div className="match-card-game-row" role="listitem" key={entry.id}>
          <span className="match-card-game-type">
            {entry.matchId
              ? <Link to={routePaths.matchSummary(entry.matchId, returnSearch)}>
                  {entry.dateTime ? new Date(entry.dateTime).toLocaleDateString(i18n.language) : t('detail.unavailableDate')}
                </Link>
              : (entry.dateTime ? new Date(entry.dateTime).toLocaleDateString(i18n.language) : t('detail.unavailableDate'))}
          </span>
          <span className="match-card-game-opponents">{entry.competition ?? t('common.unavailable')}</span>
          <span className={`match-card-game-result match-result-${entry.result}`}>
            <span className="match-card-game-outcome">{resultLabel(entry.result, t)}</span>
            <span className="match-card-game-score">{entry.playerSets == null || entry.opponentSets == null ? t('detail.unavailableScore') : `${entry.playerSets} — ${entry.opponentSets}`}</span>
          </span>
        </div>)}
      </div>
    )}
  </div>
}

function OpponentInsightsPanel({ item, t }) {
  const tiles = [
    item.closeness ? <InsightTile key="closeness" label={t('detail.insightCloseness')} hint={t('detail.insightClosenessHint')}>
      <span className={`opponent-insight-closeness opponent-insight-closeness--${item.closeness}`}>{closenessLabel(item.closeness, t)}</span>
      <span className="opponent-insight-tile-sub">{t('detail.insightAverageMargin', { value: Math.abs(item.averageMargin).toFixed(1) })}</span>
    </InsightTile> : null,
    item.singlesRecord ? <InsightTile key="singles" label={t('detail.insightSingles')} hint={t('detail.insightSinglesHint')}>
      <span>{recordText(item.singlesRecord, t)}</span>
    </InsightTile> : null,
    item.doublesRecord ? <InsightTile key="doubles" label={t('detail.insightDoubles')} hint={t('detail.insightDoublesHint')}>
      <span>{recordText(item.doublesRecord, t)}</span>
    </InsightTile> : null,
    item.homeRecord || item.awayRecord ? <InsightTile key="homeAway" label={t('detail.insightHomeAway')} hint={t('detail.insightHomeAwayHint')}>
      <span>{homeAwayText(item, t)}</span>
    </InsightTile> : null,
    item.longestWinStreak > 0 || item.longestLossStreak > 0 ? <InsightTile key="streaks" label={t('detail.insightStreaks')} hint={t('detail.insightStreaksHint')}>
      <span>{streaksText(item, t)}</span>
    </InsightTile> : null,
    item.lastPlayed ? <InsightTile key="lastMet" label={t('detail.insightLastMet')} hint={t('detail.insightLastMetHint')}>
      <span>{relativeTimeLabel(item.lastPlayed)}</span>
      {item.matchesPerSeason ? <span className="opponent-insight-tile-sub">{t('detail.insightFrequencyPerSeason', { count: item.matchesPerSeason.average.toFixed(1) })}</span> : null}
    </InsightTile> : null,
    item.trend ? <InsightTile key="trend" label={t('detail.insightTrend')} hint={t('detail.insightTrendHint')} wide>
      <span className={`opponent-insight-trend opponent-insight-trend--${item.trend.tone}`}>{trendText(item.trend, t)}</span>
    </InsightTile> : null,
    item.competitionBreakdown ? <InsightTile key="competitions" label={t('detail.insightCompetitionBreakdown')} hint={t('detail.insightCompetitionBreakdownHint')} wide>
      {item.competitionBreakdown.map((row) => <span key={row.competition}>{row.competition}: {recordText(row, t)}</span>)}
    </InsightTile> : null,
  ].filter(Boolean)
  if (tiles.length === 0) return null
  return <div className="opponent-insights-panel">
    <h5 className="opponent-insights-title">{t('detail.insightsTitle')}</h5>
    <div className="opponent-insight-tiles">{tiles}</div>
  </div>
}

function InsightTile({ label, hint, wide, children }) {
  return <div className={`opponent-insight-tile${wide ? ' opponent-insight-tile--wide' : ''}`}>
    <span className="opponent-insight-tile-label" title={hint}>{label}</span>
    <span className="opponent-insight-tile-value">{children}</span>
  </div>
}

function closenessLabel(closeness, t) {
  if (closeness === 'decisive') return t('detail.insightClosenessDecisive')
  if (closeness === 'competitive') return t('detail.insightClosenessCompetitive')
  if (closeness === 'nail-biter') return t('detail.insightClosenessNailBiter')
  return null
}

function recordText(record, t) {
  return `${record.wins}${t('detail.insightWinShort')}–${record.losses}${t('detail.insightLossShort')} (${record.matches})`
}

function roundedPercentage(value) {
  return value == null ? '—' : Math.round(value)
}

function homeAwayText(item, t) {
  const parts = []
  if (item.homeRecord) parts.push(`${t('detail.insightHomeShort')} ${roundedPercentage(winPercentage(item.homeRecord))}%`)
  if (item.awayRecord) parts.push(`${t('detail.insightAwayShort')} ${roundedPercentage(winPercentage(item.awayRecord))}%`)
  return parts.join(' · ')
}

function streaksText(item, t) {
  return `${t('detail.insightWinShort')}${item.longestWinStreak} ${t('detail.insightStreakBest')} · ${t('detail.insightLossShort')}${item.longestLossStreak} ${t('detail.insightStreakWorst')}`
}

function relativeTimeLabel(dateString) {
  const diffDays = Math.round((Date.now() - new Date(dateString).getTime()) / 86400000)
  const formatter = new Intl.RelativeTimeFormat(i18n.language, { numeric: 'auto' })
  if (Math.abs(diffDays) < 30) return formatter.format(-diffDays, 'day')
  const diffMonths = Math.round(diffDays / 30)
  if (Math.abs(diffMonths) < 12) return formatter.format(-diffMonths, 'month')
  return formatter.format(-Math.round(diffDays / 365), 'year')
}

function trendText(trend, t) {
  const key = trend.tone === 'improved' ? 'insightTrendImproved' : trend.tone === 'declining' ? 'insightTrendDeclining' : 'insightTrendStable'
  const arrow = trend.tone === 'improved' ? '↗' : trend.tone === 'declining' ? '↘' : '→'
  return `${arrow} ${t(`detail.${key}`, { previous: Math.round(trend.previousWinRate), current: Math.round(trend.currentWinRate) })}`
}

function compareMatches(left, right) {
  if (!left.dateTime && !right.dateTime) return String(left.id).localeCompare(String(right.id))
  if (!left.dateTime) return 1
  if (!right.dateTime) return -1
  return new Date(right.dateTime) - new Date(left.dateTime)
    || String(left.id).localeCompare(String(right.id))
}

function winPercentage(item) {
  const decided = item.wins + item.losses
  return decided === 0 ? null : item.wins * 100 / decided
}

function formatWinPercentage(value) {
  return value == null ? '—' : `${value.toFixed(1)}%`
}

function opponentCategory(opponent, overallWinPercentage) {
  if (opponent.wins > opponent.losses) return 'favorable'
  if (opponent.losses <= opponent.wins) return 'uncategorized'
  if ((overallWinPercentage != null && opponent.playerWinPercentage <= overallWinPercentage - 20)
    || (overallWinPercentage == null && opponent.matches >= 2)) return 'problem'
  return 'hard'
}

function categoryLabel(category, t) {
  return category === 'favorable'
    ? t('detail.categoryFavorableLabel')
    : category === 'hard'
      ? t('detail.categoryHardLabel')
      : category === 'problem'
        ? t('detail.categoryProblemLabel')
        : t('detail.categoryUnknown')
}

function OpponentCategoryBadge({ category, t }) {
  const tone = category === 'favorable' || category === 'hard' || category === 'problem' ? category : 'unknown'
  return <span className={`opponent-category-badge opponent-category-badge--${tone}`}>{categoryLabel(category, t)}</span>
}

function compareOpponentNames(left, right) {
  return left.name.localeCompare(right.name, 'ca', { sensitivity: 'base' })
    || left.name.localeCompare(right.name, 'ca')
}

function compareCategorizedOpponents(left, right) {
  if (left.playerWinPercentage == null && right.playerWinPercentage != null) return 1
  if (left.playerWinPercentage != null && right.playerWinPercentage == null) return -1
  return (right.playerWinPercentage ?? 0) - (left.playerWinPercentage ?? 0)
    || right.matches - left.matches
    || compareOpponentNames(left, right)
}

function addOpponent(opponents, key, opponent, entry) {
  const current = opponents.get(key) ?? {
    key,
    name: opponent.available ? opponent.name : null,
    matches: 0,
    wins: 0,
    draws: 0,
    losses: 0,
    history: [],
  }
  current.matches += 1
  if (entry.result === 'win') current.wins += 1
  if (entry.result === 'loss') current.losses += 1
  if (entry.result === 'draw') current.draws += 1
  const margin = entry.playerSets == null || entry.opponentSets == null ? null : entry.playerSets - entry.opponentSets
  current.history.push({ ...entry, margin })
  opponents.set(key, current)
}

function opponentKey(opponent) {
  return opponent.playerId ?? opponent.federatedPlayerId ?? opponent.playerSeasonId
    ?? `unavailable-${opponent.source ?? 'unknown'}-${opponent.season ?? 'unknown'}`
}

function buildOpponentRow(opponent) {
  const sortedHistory = [...opponent.history].sort(compareHistoryDesc)
  const margin = averageMargin(sortedHistory)
  return {
    ...opponent,
    playerWinPercentage: winPercentage(opponent),
    sortedHistory,
    recentForm: sortedHistory.slice(0, RECENT_FORM_SIZE),
    streak: currentStreak(sortedHistory),
    lastPlayed: sortedHistory[0]?.dateTime ?? null,
    averageMargin: margin,
    closeness: closenessBucket(margin),
    singlesRecord: splitRecord(sortedHistory, (entry) => entry.gameType != null && entry.gameType !== 'DOUBLES'),
    doublesRecord: splitRecord(sortedHistory, (entry) => entry.gameType === 'DOUBLES'),
    homeRecord: splitRecord(sortedHistory, (entry) => entry.isHome === true),
    awayRecord: splitRecord(sortedHistory, (entry) => entry.isHome === false),
    longestWinStreak: longestStreak(sortedHistory, 'win'),
    longestLossStreak: longestStreak(sortedHistory, 'loss'),
    matchesPerSeason: computeMatchesPerSeason(sortedHistory),
    competitionBreakdown: computeCompetitionBreakdown(sortedHistory),
    trend: opponentTrend(sortedHistory),
  }
}

function averageMargin(history) {
  const margins = history.map((entry) => entry.margin).filter((margin) => margin != null)
  if (margins.length === 0) return null
  return margins.reduce((total, margin) => total + margin, 0) / margins.length
}

function closenessBucket(margin) {
  if (margin == null) return null
  const magnitude = Math.abs(margin)
  if (magnitude >= 2) return 'decisive'
  if (magnitude >= 1) return 'competitive'
  return 'nail-biter'
}

function splitRecord(history, predicate) {
  const entries = history.filter(predicate)
  if (entries.length === 0) return null
  return {
    matches: entries.length,
    wins: entries.filter((entry) => entry.result === 'win').length,
    losses: entries.filter((entry) => entry.result === 'loss').length,
  }
}

function computeMatchesPerSeason(history) {
  const seasons = new Set(history.map((entry) => entry.season).filter(Boolean))
  if (seasons.size === 0) return null
  return { matches: history.length, seasons: seasons.size, average: history.length / seasons.size }
}

function computeCompetitionBreakdown(history) {
  const competitions = [...new Set(history.map((entry) => entry.competition).filter(Boolean))]
  if (competitions.length <= 1) return null
  return competitions.map((competition) => {
    const entries = history.filter((entry) => entry.competition === competition)
    return {
      competition,
      matches: entries.length,
      wins: entries.filter((entry) => entry.result === 'win').length,
      losses: entries.filter((entry) => entry.result === 'loss').length,
    }
  })
}

function opponentTrend(sortedHistoryDesc) {
  const windowSize = Math.min(RECENT_FORM_SIZE, Math.floor(sortedHistoryDesc.length / 2))
  if (windowSize < 2) return null
  const recentWindow = sortedHistoryDesc.slice(0, windowSize)
  const previousWindow = sortedHistoryDesc.slice(windowSize, windowSize * 2)
  return computeTrendNote(winPercentage(recordTotals(recentWindow)), winPercentage(recordTotals(previousWindow)))
}

function recordTotals(entries) {
  return {
    wins: entries.filter((entry) => entry.result === 'win').length,
    losses: entries.filter((entry) => entry.result === 'loss').length,
  }
}

function gameSetsForPlayer(match, game) {
  const isHome = match.playerTeam === match.homeTeam
  return {
    playerSets: isHome ? game.homeSetsWon : game.awaySetsWon,
    opponentSets: isHome ? game.awaySetsWon : game.homeSetsWon,
  }
}

function matchGamesForPlayer(match) {
  const isHome = match.playerTeam === match.homeTeam
  return {
    playerGames: isHome ? match.homeGamesWon : match.awayGamesWon,
    opponentGames: isHome ? match.awayGamesWon : match.homeGamesWon,
  }
}

function compareHistoryDesc(left, right) {
  if (!left.dateTime && !right.dateTime) return String(right.id).localeCompare(String(left.id))
  if (!left.dateTime) return 1
  if (!right.dateTime) return -1
  return new Date(right.dateTime) - new Date(left.dateTime) || String(right.id).localeCompare(String(left.id))
}

function currentStreak(sortedHistoryDesc) {
  if (sortedHistoryDesc.length === 0) return null
  const type = sortedHistoryDesc[0].result
  let count = 0
  for (const entry of sortedHistoryDesc) {
    if (entry.result !== type) break
    count += 1
  }
  return { type, count }
}

function streakLabel(streak, t) {
  if (!streak || streak.count === 0) return t('detail.noStreak')
  if (streak.type === 'win') return t('detail.streakWin', { count: streak.count })
  if (streak.type === 'loss') return t('detail.streakLoss', { count: streak.count })
  if (streak.type === 'draw') return t('detail.streakDraw', { count: streak.count })
  return t('detail.noStreak')
}

function aggregateCareerStatistics(matches) {
  const wins = matches.filter((match) => match.result === 'win').length
  const losses = matches.filter((match) => match.result === 'loss').length
  const draws = matches.filter((match) => match.result === 'draw').length
  const games = matches.flatMap((match) => (match.games ?? []).map((game) => ({ match, game })))
  const singlesGames = games.filter(({ game }) => game.type !== 'DOUBLES')
  const doublesGames = games.filter(({ game }) => game.type === 'DOUBLES')
  const margins = games
    .map(({ match, game }) => gameSetsForPlayer(match, game))
    .filter((sets) => sets.playerSets != null && sets.opponentSets != null)
    .map((sets) => sets.playerSets - sets.opponentSets)
  const sortedMatchesDesc = [...matches].sort(compareMatches)
  return {
    matchesPlayed: matches.length,
    wins,
    losses,
    draws,
    winPercentage: winPercentage({ wins, losses }),
    singlesWinPercentage: winPercentage(gameResultTotals(singlesGames)),
    doublesWinPercentage: winPercentage(gameResultTotals(doublesGames)),
    averageSetMargin: margins.length === 0 ? null : margins.reduce((total, margin) => total + margin, 0) / margins.length,
    currentStreak: currentStreak(sortedMatchesDesc),
    longestWinStreak: longestStreak(sortedMatchesDesc, 'win'),
  }
}

function gameResultTotals(entries) {
  return entries.reduce((totals, { game }) => ({
    wins: totals.wins + (game.result === 'win' ? 1 : 0),
    losses: totals.losses + (game.result === 'loss' ? 1 : 0),
  }), { wins: 0, losses: 0 })
}

function longestStreak(sortedMatchesDesc, type) {
  let longest = 0
  let current = 0
  for (const match of sortedMatchesDesc) {
    if (match.result === type) {
      current += 1
      longest = Math.max(longest, current)
    } else {
      current = 0
    }
  }
  return longest
}

function matchIndividualScore(match) {
  const games = match.games ?? []
  const game = games.find((item) => item.type !== 'DOUBLES') ?? games[0]
  if (game) {
    const sets = gameSetsForPlayer(match, game)
    if (sets.playerSets != null && sets.opponentSets != null) return sets
  }
  const { playerGames, opponentGames } = matchGamesForPlayer(match)
  return { playerSets: playerGames, opponentSets: opponentGames }
}

function matchQualityTier(match) {
  const { playerSets, opponentSets } = matchIndividualScore(match)
  const margin = playerSets == null || opponentSets == null ? null : playerSets - opponentSets
  return qualityTier(match.result, margin)
}

function tierScoreLabel(points, tier) {
  const point = points.find((item) => item.tier === tier)
  if (!point) return null
  const { playerSets, opponentSets } = matchIndividualScore(point.match)
  if (playerSets == null || opponentSets == null) return null
  return `${playerSets}-${opponentSets}`
}

function qualityTier(result, margin) {
  if (result === 'draw') return 'draw'
  if (result !== 'win' && result !== 'loss') return null
  if (margin == null) return result
  const magnitude = Math.abs(margin)
  if (result === 'win') return magnitude >= 3 ? 'strong-win' : magnitude === 2 ? 'win' : 'close-win'
  return magnitude >= 3 ? 'strong-loss' : magnitude === 2 ? 'loss' : 'close-loss'
}

function qualityLabel(tier, t) {
  switch (tier) {
    case 'strong-win': return t('detail.qualityStrongWin')
    case 'win': return t('detail.qualityWin')
    case 'close-win': return t('detail.qualityCloseWin')
    case 'draw': return t('detail.qualityDraw')
    case 'close-loss': return t('detail.qualityCloseLoss')
    case 'loss': return t('detail.qualityLoss')
    case 'strong-loss': return t('detail.qualityStrongLoss')
    default: return t('common.unavailable')
  }
}

function opponentComparator(sort) {
  if (sort === OPPONENT_SORTS.WIN_PERCENTAGE) {
    return (left, right) => (right.playerWinPercentage ?? -1) - (left.playerWinPercentage ?? -1) || compareOpponentNames(left, right)
  }
  if (sort === OPPONENT_SORTS.MATCHES) {
    return (left, right) => right.matches - left.matches || compareOpponentNames(left, right)
  }
  if (sort === OPPONENT_SORTS.LAST_PLAYED) {
    return (left, right) => {
      if (!left.lastPlayed && !right.lastPlayed) return compareOpponentNames(left, right)
      if (!left.lastPlayed) return 1
      if (!right.lastPlayed) return -1
      return new Date(right.lastPlayed) - new Date(left.lastPlayed) || compareOpponentNames(left, right)
    }
  }
  if (sort === OPPONENT_SORTS.CLOSENESS) {
    return (left, right) => {
      if (left.averageMargin == null && right.averageMargin == null) return compareOpponentNames(left, right)
      if (left.averageMargin == null) return 1
      if (right.averageMargin == null) return -1
      return Math.abs(left.averageMargin) - Math.abs(right.averageMargin) || compareOpponentNames(left, right)
    }
  }
  return null
}

function sanitizeId(value) {
  return String(value).replace(/[^a-zA-Z0-9-_]/g, '-')
}

export function MatchOpponentDetails({ match }) {
  const { t } = useTranslation()
  const games = match.games ?? []
  return <details className="opponent-match card">
    <summary>{match.competition} · {match.dateTime ? new Date(match.dateTime).toLocaleDateString(i18n.language) : t('detail.unavailableDate')} · {scoreLabel(match, t)}</summary>
    <div className="opponent-match-content">
      <p><strong>{t('detail.resultTeams')}</strong> {resultLabel(match.result, t)} · <strong>{t('detail.teams')}</strong> {match.homeTeam} — {match.awayTeam}</p>
      {games.length === 0 ? <p>{t('detail.unavailableDetails')}</p> : games.map((game) => (
        <div className="opponent-game" key={game.id}>
          <strong>{t('detail.game', { number: game.gameNumber })} · {game.type === 'DOUBLES' ? t('detail.doubles') : t('detail.singles')}</strong>
          <span> {resultLabel(game.result, t)} · {game.homeSetsWon == null || game.awaySetsWon == null ? t('detail.unavailableScore') : `${game.homeSetsWon} — ${game.awaySetsWon}`}</span>
          <p>{t('detail.opponents')}: {game.opponents.length === 0 ? t('detail.unavailablePlural') : game.opponents.map((opponent) => opponent.available ? opponent.name : t('common.unavailable')).join(', ')}</p>
          {game.unavailableReason ? <p>{game.unavailableReason}</p> : null}
        </div>
      ))}
    </div>
  </details>
}

function opponentName(match) {
  return match.playerTeam === match.homeTeam ? match.awayTeam : match.homeTeam
}

function matchGameRows(match, t) {
  return gamesWithOpponentInfo(match).map((game) => ({
    id: game.id,
    result: game.result,
    typeLabel: game.type === 'DOUBLES' ? t('detail.doubles') : t('detail.singles'),
    opponents: gameOpponentNames(game, t),
    resultLabel: resultLabel(game.result, t),
    scoreLabel: game.homeSetsWon == null || game.awaySetsWon == null ? t('detail.unavailableScore') : `${game.homeSetsWon}-${game.awaySetsWon}`,
  }))
}

function gameOpponentNames(game, t) {
  const opponents = new Map()
  game.opponents.forEach((opponent) => {
    const key = opponentKey(opponent)
    if (!opponents.has(key)) {
      opponents.set(key, {
        key,
        name: opponent.available ? opponent.name : t('common.unavailable'),
        playerId: opponent.available ? opponent.playerId : null,
      })
    }
  })
  return opponents.size > 0 ? [...opponents.values()] : [{ key: 'unavailable', name: t('common.unavailable'), playerId: null }]
}

function gamesWithOpponentInfo(match) {
  return (match.games ?? []).filter((game) => game.opponents.some((opponent) => opponent.available && opponent.name))
}

function opponentTeamName(match, t) {
  return opponentName(match) || t('common.unavailable')
}

function resultLabel(result, t) {
  return result === 'win'
    ? t('detail.win')
    : result === 'loss'
      ? t('detail.loss')
      : result === 'draw'
        ? t('detail.draw')
        : t('common.unavailable')
}

function resultBadgeLabel(result, t) {
  return result === 'win'
    ? t('detail.resultBadgeWin')
    : result === 'loss'
      ? t('detail.resultBadgeLoss')
      : result === 'draw'
        ? t('detail.resultBadgeDraw')
        : t('common.unavailable')
}

function scoreLabel(match, t) {
  return match.homeGamesWon == null || match.awayGamesWon == null
    ? t('common.noData')
    : `${match.homeGamesWon} — ${match.awayGamesWon}`
}

function activateTab(event, view, update, key = 'view') {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    update(key, view)
  }
}

function ConnectedScatterPlot({ values }) {
  const { t } = useTranslation()
  const width = 640
  const height = 220
  const padding = { top: 16, right: 48, bottom: 36, left: 54 }
  const plotWidth = width - padding.left - padding.right
  const plotHeight = height - padding.top - padding.bottom
  const maxMatches = Math.max(...values.map((item) => item.matchesPlayed), 1)
  const matchTicks = matchAxisTicks(maxMatches)
  const x = (index) => values.length === 1
    ? padding.left + plotWidth / 2
    : padding.left + index * plotWidth / (values.length - 1)
  const yMatches = (value) => padding.top + plotHeight - value / maxMatches * plotHeight
  const yWins = (value) => padding.top + plotHeight - (value ?? 0) / 100 * plotHeight
  const matchesPoints = values.map((item, index) => `${x(index)},${yMatches(item.matchesPlayed)}`).join(' ')
  const winsPoints = values
    .filter((item) => item.winPercentage != null)
    .map((item) => `${x(values.indexOf(item))},${yWins(item.winPercentage)}`)
    .join(' ')

  return <div className="history-chart history-connected-chart card chart-connected-scatter" role="img"
    aria-label={t('detail.chartAria')}>
    <svg viewBox={`0 0 ${width} ${height}`} role="presentation" focusable="false" preserveAspectRatio="xMidYMid meet">
      {PERCENTAGE_TICKS.map((tick) => {
        const y = yWins(tick)
        return <g key={tick}>
          <line className="chart-grid-line percentage-grid-line" x1={padding.left} y1={y} x2={width - padding.right} y2={y} />
          <text className="chart-axis-tick percentage-axis-tick" x={width - padding.right + 6} y={y + 3} textAnchor="start">{tick}%</text>
        </g>
      })}
      {matchTicks.map((tick) => {
        const y = yMatches(tick)
        return <g key={tick}>
          <text className="chart-axis-tick matches-axis-tick" x={padding.left - 6} y={y + 3} textAnchor="end">{tick}</text>
        </g>
      })}
      <polyline className="chart-line matches-line" fill="none" points={matchesPoints} />
      {winsPoints && <polyline className="chart-line wins-line" fill="none" points={winsPoints} />}
      {values.map((item, index) => <g key={`${item.source}-${item.season}-${index}`}>
        <path className="chart-point matches-point" d={crossPath(x(index), yMatches(item.matchesPlayed), MATCHES_MARKER_SIZE)} />
        {item.winPercentage != null && <polygon className="chart-point wins-point" points={trianglePoints(x(index), yWins(item.winPercentage), WINS_MARKER_SIZE)} />}
        <text className="chart-season-label" x={x(index)} y={height - 18} textAnchor="middle">{item.season || '—'}</text>
      </g>)}
      <text className="chart-axis-label matches-axis-label" x="14" y={padding.top + plotHeight / 2} textAnchor="middle" transform={`rotate(-90 14 ${padding.top + plotHeight / 2})`}>{t('common.playedMatches')}</text>
      <text className="chart-axis-label percentage-axis-label" x={width - 10} y={padding.top + plotHeight / 2} textAnchor="middle" transform={`rotate(90 ${width - 10} ${padding.top + plotHeight / 2})`}>{t('common.winPercentage')}</text>
      <text className="chart-axis-label" x={width / 2} y={height - 2} textAnchor="middle">{t('common.seasons')}</text>
    </svg>
  </div>
}

function MatchQualitySpectrumChart({ matches, t }) {
  const width = 640
  const height = 220
  const padding = { top: 16, right: 16, bottom: 48, left: 116 }
  const plotWidth = width - padding.left - padding.right
  const plotHeight = height - padding.top - padding.bottom
  const sortedMatches = [...matches].sort(compareMatches).reverse()
  const x = (index) => sortedMatches.length === 1
    ? padding.left + plotWidth / 2
    : padding.left + index * plotWidth / (sortedMatches.length - 1)
  const y = (tier) => {
    const index = SPECTRUM_TIERS.indexOf(tier)
    return index === -1 ? null : padding.top + index * plotHeight / (SPECTRUM_TIERS.length - 1)
  }
  const points = sortedMatches
    .map((match, index) => ({ match, index, tier: matchQualityTier(match) }))
    .filter((item) => item.tier != null)
  const dateTicks = timelineTicks(sortedMatches)
  const axisY = height - padding.bottom

  return <div className="history-chart history-spectrum-chart card chart-match-spectrum" role="img"
    aria-label={t('detail.spectrumChartAria')}>
    <svg viewBox={`0 0 ${width} ${height}`} role="presentation" focusable="false" preserveAspectRatio="xMidYMid meet">
      {SPECTRUM_TIERS.map((tier) => {
        const tickY = y(tier)
        const scoreLabel = tierScoreLabel(points, tier)
        return <g key={tier}>
          <line className="chart-grid-line spectrum-grid-line" x1={padding.left} y1={tickY} x2={width - padding.right} y2={tickY} />
          <text className="chart-axis-tick spectrum-axis-tick" x={padding.left - 16} y={tickY + 3} textAnchor="end">
            {scoreLabel ?? qualityLabel(tier, t)}
            <title>{qualityLabel(tier, t)}</title>
          </text>
        </g>
      })}
      {points.map((item) => (
        <circle key={item.match.id} className={`spectrum-point quality-fill-${item.tier}`}
          cx={x(item.index)} cy={y(item.tier)} r={MATCHES_MARKER_SIZE} />
      ))}
      <line className="chart-axis-line" x1={padding.left} y1={axisY} x2={width - padding.right} y2={axisY} />
      {dateTicks.map((tick) => (
        <g key={tick.index}>
          <line className="chart-axis-tick-mark" x1={x(tick.index)} y1={axisY} x2={x(tick.index)} y2={axisY + 4} />
          <text className="chart-axis-tick spectrum-date-tick" x={x(tick.index)} y={axisY + 16} textAnchor="middle">{tick.label}</text>
        </g>
      ))}
      <text className="chart-axis-label" x={width / 2} y={height - 2} textAnchor="middle">{t('detail.matchOrder')}</text>
    </svg>
  </div>
}

function timelineTicks(sortedMatches) {
  if (sortedMatches.length === 0) return []
  const tickCount = Math.min(5, sortedMatches.length)
  const indices = [...new Set(Array.from({ length: tickCount },
    (_, step) => Math.round(step * (sortedMatches.length - 1) / Math.max(tickCount - 1, 1))))]
  return indices.map((index) => ({
    index,
    label: matchDateLabel(sortedMatches[index]),
  }))
}

function matchDateLabel(match) {
  return match.dateTime ? new Date(match.dateTime).toLocaleDateString(i18n.language) : '—'
}

function trianglePoints(centerX, centerY, size) {
  return `${centerX},${centerY - size} ${centerX - size},${centerY + size} ${centerX + size},${centerY + size}`
}

function crossPath(centerX, centerY, size) {
  return `M ${centerX - size} ${centerY - size} L ${centerX + size} ${centerY + size} M ${centerX + size} ${centerY - size} L ${centerX - size} ${centerY + size}`
}

function matchAxisTicks(maxMatches) {
  return [...new Set([0, 1, 2, 3, 4].map((step) => Math.round(maxMatches * step / 4)))]
}

function aggregateCompetition(matches, competition) {
  const selected = [...new Map(
    matches
      .filter((item) => item.competition === competition)
      .map((item) => [item.id, item]),
  ).values()]
  const grouped = new Map()
  selected.forEach((match) => {
    const key = `${match.source}-${match.season}`
    const current = grouped.get(key) ?? { source: match.source, season: match.season, matchesPlayed: 0, wins: 0, losses: 0 }
    current.matchesPlayed += 1
    if (match.result === 'win') current.wins += 1
    if (match.result === 'loss') current.losses += 1
    grouped.set(key, current)
  })
  return [...grouped.values()].map((item) => ({ ...item, winPercentage: item.wins + item.losses ? item.wins * 100 / (item.wins + item.losses) : null }))
}

export default PlayerDetailPage
