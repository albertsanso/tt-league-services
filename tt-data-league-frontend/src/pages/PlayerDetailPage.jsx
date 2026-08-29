import { ArrowLeft } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerDetails } from '../hooks/usePlayers.js'

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
const OPPONENT_VIEWS = {
  CATEGORIZATION: 'categorization',
  SEARCH: 'search',
}
const CHART_TYPES = ['line', 'bar', 'connected-scatter']
const CHART_LABELS = {
  line: 'Línies',
  bar: 'Barres',
  'connected-scatter': 'Dispersió connectada',
}
const unique = (values) => [...new Set(values.filter(Boolean))].sort()
const MATCHES_PER_PAGE = 10

function PlayerDetailPage() {
  const { playerId } = useParams()
  const [params, setParams] = useSearchParams()
  const sourceFilter = params.get('source') === ALL ? '' : params.get('source') || ''
  const seasonFilter = params.get('season') === ALL ? '' : params.get('season') || ''
  const competitionFilter = params.get('competition') === ALL ? '' : params.get('competition') || ''
  const { data, loading, error, retry } = usePlayerDetails(
    playerId, sourceFilter, seasonFilter, competitionFilter,
  )
  if (loading && !data) return <p className="club-state card" role="status">Carregant el jugador...</p>
  if (error?.status === 404 || error?.status === 400) {
    return <section className="page-block" role="alert">
      <h1 className="page-title">Jugador no trobat</h1>
      <p className="page-description">El jugador sol·licitat no existeix o l’identificador no és vàlid.</p>
      <Link className="secondary-button" to={routePaths.players()}>Torna a la cerca</Link>
    </section>
  }
  if (error || !data) {
    return <section className="page-block" role="alert">
      <h1 className="page-title">No s’ha pogut carregar el jugador</h1>
      <p className="page-description">
        {error?.status === 401
          ? 'La sessió ha caducat. Torna a iniciar sessió per continuar.'
          : 'Hi ha hagut un problema en consultar aquesta informació.'}
      </p>
      <button className="secondary-button" type="button" onClick={retry}>Reintenta</button>
    </section>
  }
  return (
    <>
      {loading ? <p className="visually-hidden" role="status" aria-live="polite">Actualitzant les dades del jugador...</p> : null}
      <PlayerDetailContent data={data} params={params} setParams={setParams} />
    </>
  )
}

function PlayerDetailContent({ data, params, setParams }) {
  const requestedView = params.get('view')
  const view = Object.values(VIEWS).includes(requestedView) ? requestedView : VIEWS.STATISTICS
  const requestedOpponentView = params.get('opponentView')
  const opponentView = Object.values(OPPONENT_VIEWS).includes(requestedOpponentView)
    ? requestedOpponentView
    : OPPONENT_VIEWS.CATEGORIZATION
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
    'Totes les temporades'.length,
    ...seasons.map((item) => item.length),
  )
  const selectedSeason = params.get('season')
  const season = selectedSeason === ALL ? '' : seasons.includes(selectedSeason) ? selectedSeason : ''
  const availableCompetitions = unique(data.competitions
    .filter((item) => (!source || item.source === source) && (!season || item.season === season))
    .map((item) => item.name))
  const selectedCompetition = params.get('competition')
  const competition = availableCompetitions.includes(selectedCompetition) ? selectedCompetition : ''
  const requestedChart = params.get('chart')
  const chart = CHART_TYPES.includes(requestedChart) ? requestedChart : 'line'
  const matches = data.matches
  const statistics = data.statistics

  useEffect(() => {
    const shouldNormalizeView = requestedView !== view
    const hasInvalidOpponentView = requestedOpponentView != null
      && !Object.values(OPPONENT_VIEWS).includes(requestedOpponentView)
    const shouldNormalizeOpponentView = view === VIEWS.OPPONENTS && requestedOpponentView !== opponentView
    if (shouldNormalizeView || shouldNormalizeOpponentView || hasInvalidOpponentView) {
      const next = new URLSearchParams(params)
      next.set('view', view)
      if (view === VIEWS.OPPONENTS || hasInvalidOpponentView) next.set('opponentView', opponentView)
      setParams(next, { replace: true })
    }
  }, [opponentView, params, requestedOpponentView, requestedView, setParams, view])

  function update(key, value) {
    const next = new URLSearchParams(params)
    next.set(key, value || ALL)
    if (key === 'view' && value === VIEWS.OPPONENTS && !Object.values(OPPONENT_VIEWS).includes(next.get('opponentView'))) {
      next.set('opponentView', OPPONENT_VIEWS.CATEGORIZATION)
    }
    if (key === 'source') {
      next.set('season', ALL)
      next.delete('competition')
    }
    if (key === 'season') next.delete('competition')
    setParams(next)
  }

  return (
    <section className="page-block player-detail-page" aria-labelledby="player-detail-title">
      <Link className="back-link" to={routePaths.players()}> <ArrowLeft size={16} aria-hidden="true" /> Torna a la cerca</Link>
      <div className="club-detail-header">
        <div>
          <p className="section-label">Identitat canònica</p>
          <h1 id="player-detail-title" className="page-title">{data.name}</h1>
          <p className="club-source">UUID: {data.id}</p>
        </div>
      </div>
      <div className="player-detail-controls">
      <div className="club-filters">
        <fieldset className="club-filter season-slider" style={{ '--season-label-width': `${seasonLabelWidth}ch` }}><legend>Temporada</legend>
          <output htmlFor="player-season">{season || 'Totes les temporades'}</output>
          <input id="player-season" type="range" min="0" max={Math.max(seasons.length, 0)} step="1"
            value={season ? seasons.indexOf(season) : seasons.length}
            onChange={(event) => update('season', seasons[Number(event.target.value)] ?? ALL)}
            disabled={seasons.length === 0}
            aria-label="Selecciona la temporada" />
          {seasons.length > 0 ? <div className="season-marks" aria-hidden="true">
            {seasons.map((item) => <span key={item}>{item}</span>)}
            <span>Totes</span>
          </div> : null}
          <button className="filter-all-button" type="button" onClick={() => update('season', ALL)}>Totes les temporades</button>
        </fieldset>
        <fieldset className="club-filter source-options"><legend>Font</legend>
          <label><input type="radio" name="player-source" checked={!source} onChange={() => update('source', ALL)} /> Totes les fonts</label>
          {sources.map((item) => <label key={item}><input type="radio" name="player-source" value={item} checked={source === item} onChange={() => update('source', item)} /> {item}</label>)}
        </fieldset>
        <label className="club-filter player-competition-filter"><span>Competició</span><select value={competition} onChange={(event) => update('competition', event.target.value)}>
          <option value="">Totes les competicions</option>{availableCompetitions.map((item) => <option key={item} value={item}>{item}</option>)}
        </select></label>
      </div>
      <div className="club-tabs player-tabs" role="tablist" aria-label="Vistes del jugador">
        <button id="player-statistics-tab" className={`club-tab${view === VIEWS.STATISTICS ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.STATISTICS} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.STATISTICS)} onKeyDown={(event) => activateTab(event, VIEWS.STATISTICS, update)}>Estadístiques</button>
        <button id="player-matches-tab" className={`club-tab${view === VIEWS.MATCHES ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.MATCHES} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.MATCHES)} onKeyDown={(event) => activateTab(event, VIEWS.MATCHES, update)}>Partits</button>
        <button id="player-opponents-tab" className={`club-tab${view === VIEWS.OPPONENTS ? ' is-active' : ''}`} type="button" role="tab" aria-selected={view === VIEWS.OPPONENTS} aria-controls="player-tabpanel" onClick={() => update('view', VIEWS.OPPONENTS)} onKeyDown={(event) => activateTab(event, VIEWS.OPPONENTS, update)}>Anàlisi d'oponents</button>
      </div>
      </div>
      <div id="player-tabpanel" role="tabpanel" aria-labelledby={`player-${view}-tab`}>
        {view === VIEWS.STATISTICS ? <HistorySection statistics={statistics} competition={competition} matches={matches} chart={chart} update={update} /> : null}
        {view === VIEWS.MATCHES ? <MatchHistoryPanel key={`${source}-${season}-${competition}`} matches={matches} /> : null}
        {view === VIEWS.OPPONENTS ? <OpponentAnalysisPanel key={opponentView} matches={matches} opponentView={opponentView} update={update} /> : null}
      </div>
    </section>
  )
}

function HistorySection({ statistics, competition, matches, chart, update }) {
  const values = [...(competition ? aggregateCompetition(matches, competition) : statistics)].sort(compareSeasons)
  const chartValues = [...values].sort(compareSeasonsAscending)
  return <section className="club-detail-section" aria-labelledby="player-history-title">
    <h2 id="player-history-title">Historial estadístic</h2>
    <label className="chart-type-selector">Tipus de gràfic
      <select value={chart} onChange={(event) => update('chart', event.target.value)}>
        {CHART_TYPES.map((type) => <option key={type} value={type}>{CHART_LABELS[type]}</option>)}
      </select>
    </label>
    {values.length === 0 ? <p className="club-empty card" role="status">No hi ha dades estadístiques disponibles per als filtres seleccionats.</p> : (
      <>
        {chart === 'connected-scatter' ? <ConnectedScatterPlot values={chartValues} /> : (
          <div className={`history-chart card chart-${chart}`} role="img" aria-label={`${CHART_LABELS[chart]} de partits i percentatge de victòries per temporada`}>
            <span className="chart-axis chart-axis-y">Valor (partits, %)</span>
            {chartValues.map((item, index) => <div className="history-bar-group" key={`${item.source}-${item.season}-${index}`}>
              <div className="history-bars" aria-hidden="true">
                <span className="history-bar matches" style={{ height: `${Math.max(8, Math.min(100, item.matchesPlayed * 12))}%` }} />
                <span className="history-bar wins" style={{ height: `${item.winPercentage == null ? 8 : Math.max(8, item.winPercentage)}%` }} />
              </div>
              <strong>{item.season || 'Sense temporada'}</strong>
              <span>{item.matchesPlayed} partits · {item.winPercentage == null ? 'Percentatge no disponible' : `${item.winPercentage.toFixed(1)}% victòries`}</span>
            </div>)}
            <span className="chart-axis chart-axis-x">Temporades</span>
          </div>
        )}
        <p className="history-legend" aria-label="Llegenda del gràfic">
          <span className="legend-matches">Partits jugats</span>
          <span className="legend-wins">Victòries (%)</span>
        </p>
        <div className="table-wrap">
          <table className="history-table">
            <caption>Valors de l’historial estadístic</caption>
            <thead><tr><th>Temporada</th><th>Partits jugats</th><th>Victòries %</th></tr></thead>
            <tbody>{values.map((item, index) => <tr key={`${item.source}-${item.season}-${index}`}><td>{item.season || '—'}</td><td>{item.matchesPlayed}</td><td>{item.winPercentage == null ? '—' : `${item.winPercentage.toFixed(1)}%`}</td></tr>)}</tbody>
          </table>
        </div>
      </>
    )}
  </section>
}

function MatchHistoryPanel({ matches }) {
  const [page, setPage] = useState(0)
  const sortedMatches = [...matches].sort(compareMatches)
  const pageCount = Math.ceil(sortedMatches.length / MATCHES_PER_PAGE)
  const visibleMatches = sortedMatches.slice(page * MATCHES_PER_PAGE, (page + 1) * MATCHES_PER_PAGE)

  return <section className="club-detail-section" aria-labelledby="player-matches-title">
    <h2 id="player-matches-title">Partits</h2>
    {matches.length === 0 ? <p className="club-empty card" role="status">No hi ha partits per als filtres seleccionats.</p> : (
      <div className="match-history">
        <div className="table-wrap">
          <table className="history-table">
            <caption>Historial de partits</caption>
            <thead><tr><th>Data</th><th>Font</th><th>Temporada</th><th>Competició</th><th>Oponent</th><th>Resultat</th><th>Marcador</th></tr></thead>
            <tbody>{visibleMatches.map((item) => <tr key={item.id}>
              <td>{item.dateTime ? new Date(item.dateTime).toLocaleDateString('ca-ES') : '—'}</td>
              <td>{item.source}</td>
              <td>{item.season}</td>
              <td>{item.competition}</td>
              <td>{opponentName(item)}</td>
              <td>{resultLabel(item.result)}</td>
              <td>{scoreLabel(item)}</td>
            </tr>)}</tbody>
          </table>
        </div>
        {pageCount > 1 ? <nav className="pagination" aria-label="Paginació de partits">
          <button type="button" onClick={() => setPage((current) => current - 1)} disabled={page === 0}>Anterior</button>
          <span aria-live="polite">Pàgina {page + 1} de {pageCount}</span>
          <button type="button" onClick={() => setPage((current) => current + 1)} disabled={page === pageCount - 1}>Següent</button>
        </nav> : null}
      </div>
    )}
  </section>
}

function OpponentAnalysisPanel({ matches, opponentView, update }) {
  const [search, setSearch] = useState('')
  const opponents = new Map()
  matches.forEach((match) => {
    const opponentKeys = new Set()
    const games = match.games ?? []
    games.forEach((game) => game.opponents.forEach((opponent) => {
      const key = opponentKey(opponent)
      if (opponentKeys.has(key)) return
      opponentKeys.add(key)
      addOpponent(opponents, key, opponent, match.result)
    }))
    if (match.games == null || match.games.length === 0) {
      const opponent = { name: opponentName(match), available: true }
      addOpponent(opponents, `legacy-${opponent.name}`, opponent, match.result)
    } else if (opponentKeys.size === 0) {
      addOpponent(opponents, `unavailable-${match.id}`, { name: null, available: false }, match.result)
    }
  })
  const rows = [...opponents.values()].map((opponent) => ({
    ...opponent,
    playerWinPercentage: winPercentage(opponent),
  }))
  const overallWinPercentage = winPercentage(matches.reduce((totals, match) => ({
    wins: totals.wins + (match.result === 'win' ? 1 : 0),
    losses: totals.losses + (match.result === 'loss' ? 1 : 0),
  }), { wins: 0, losses: 0 }))
  const categorizedRows = rows.map((opponent) => ({
    ...opponent,
    category: opponentCategory(opponent, overallWinPercentage),
  }))
  const searchRows = categorizedRows
    .filter((opponent) => opponent.name.toLocaleLowerCase('ca-ES').includes(search.toLocaleLowerCase('ca-ES')))
    .sort(compareOpponentNames)

  return <section className="club-detail-section" aria-labelledby="player-opponents-title">
    <h2 id="player-opponents-title">Anàlisi d'oponents</h2>
    <div className="club-tabs opponent-tabs" role="tablist" aria-label="Vistes d'anàlisi d'oponents">
      <button id="opponent-categorization-tab" className={`club-tab${opponentView === OPPONENT_VIEWS.CATEGORIZATION ? ' is-active' : ''}`} type="button" role="tab" aria-selected={opponentView === OPPONENT_VIEWS.CATEGORIZATION} aria-controls="opponent-tabpanel" onClick={() => update('opponentView', OPPONENT_VIEWS.CATEGORIZATION)} onKeyDown={(event) => activateTab(event, OPPONENT_VIEWS.CATEGORIZATION, update, 'opponentView')}>Categorització d'oponents</button>
      <button id="opponent-search-tab" className={`club-tab${opponentView === OPPONENT_VIEWS.SEARCH ? ' is-active' : ''}`} type="button" role="tab" aria-selected={opponentView === OPPONENT_VIEWS.SEARCH} aria-controls="opponent-tabpanel" onClick={() => update('opponentView', OPPONENT_VIEWS.SEARCH)} onKeyDown={(event) => activateTab(event, OPPONENT_VIEWS.SEARCH, update, 'opponentView')}>Cerca d'oponents</button>
    </div>
    <div id="opponent-tabpanel" role="tabpanel" aria-labelledby={`opponent-${opponentView}-tab`}>
      {opponentView === OPPONENT_VIEWS.CATEGORIZATION ? (
        <>
          <OpponentCategoryTable id="favorable" title="Oponents favorables" empty="No hi ha oponents favorables per als filtres seleccionats." rows={categorizedRows.filter((opponent) => opponent.category === 'favorable').sort(compareFavorableOpponents)} />
          <OpponentCategoryTable id="hard" title="Oponents difícils" empty="No hi ha oponents difícils per als filtres seleccionats." rows={categorizedRows.filter((opponent) => opponent.category === 'hard').sort(compareHardOpponents)} />
          <OpponentCategoryTable id="problem" title="Oponents problemàtics" empty="No hi ha oponents problemàtics per als filtres seleccionats." rows={categorizedRows.filter((opponent) => opponent.category === 'problem').sort(compareHardOpponents)} />
        </>
      ) : (
        <>
          <label className="opponent-search">
            <span>Cerca un oponent</span>
            <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
          {searchRows.length === 0 ? <p className="club-empty card" role="status">Cap oponent coincideix amb la cerca.</p> : (
            <OpponentTable rows={searchRows} includeCategory summaryText={`${searchRows.length} oponents coincideixen amb la cerca.`} />
          )}
        </>
      )}
    </div>
  </section>
}

function OpponentCategoryTable({ id, title, empty, rows }) {
  return <section className="opponent-category" aria-labelledby={`opponent-category-${id}-title`}>
    <h3 id={`opponent-category-${id}-title`}>{title}</h3>
    {rows.length === 0 ? <p className="club-empty card" role="status">{empty}</p> : (
      <OpponentTable rows={rows} summaryText={`${rows.length} oponents en aquesta categoria.`} />
    )}
  </section>
}

function OpponentTable({ rows, includeCategory = false, summaryText }) {
  const [expanded, setExpanded] = useState(false)
  const maxVisible = 3
  const visibleRows = rows.slice(0, maxVisible)
  const hiddenRows = rows.slice(maxVisible)
  const descriptionId = `opponent-table-description-${rows.map((row) => row.key).join('-')}`
  const table = (tableRows) => <table className="history-table" aria-describedby={descriptionId}>
      <caption>Resultats per oponent</caption>
      <thead><tr><th>Oponent</th><th>Partits jugats</th><th>Victòries</th><th>Empats</th><th>Derrotes</th><th>Victòries %</th>{includeCategory ? <th>Categoria</th> : null}</tr></thead>
      <tbody>{tableRows.map((item) => <tr key={item.key}>
        <td>{item.name}</td>
        <td>{item.matches}</td>
        <td>{item.wins}</td>
        <td>{item.draws}</td>
        <td>{item.losses}</td>
        <td>{formatWinPercentage(item.playerWinPercentage)}</td>
        {includeCategory ? <td>{categoryLabel(item.category)}</td> : null}
      </tr>)}</tbody>
    </table>
  return <div className="opponent-table">
    <p id={descriptionId} className="visually-hidden">{summaryText}</p>
    <div className="table-wrap">{table(expanded ? rows : visibleRows)}</div>
    {hiddenRows.length > 0 && !expanded ? <div className="opponent-more">
      <button type="button" onClick={() => setExpanded((current) => !current)}>
        {`Mostra ${hiddenRows.length} oponents més`}
      </button>
    </div> : null}
  </div>
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

function categoryLabel(category) {
  return category === 'favorable'
    ? 'Favorable'
    : category === 'hard'
      ? 'Difícil'
      : category === 'problem'
        ? 'Problemàtic'
        : 'Sense categoria'
}

function compareOpponentNames(left, right) {
  return left.name.localeCompare(right.name, 'ca', { sensitivity: 'base' })
    || left.name.localeCompare(right.name, 'ca')
}

function compareFavorableOpponents(left, right) {
  return right.playerWinPercentage - left.playerWinPercentage || compareOpponentNames(left, right)
}

function compareHardOpponents(left, right) {
  return left.playerWinPercentage - right.playerWinPercentage || compareOpponentNames(left, right)
}

function addOpponent(opponents, key, opponent, result) {
  const current = opponents.get(key) ?? {
    key,
    name: opponent.available ? opponent.name : 'Oponent no disponible',
    matches: 0,
    wins: 0,
    draws: 0,
    losses: 0,
  }
  current.matches += 1
  if (result === 'win') current.wins += 1
  if (result === 'loss') current.losses += 1
  if (result === 'draw') current.draws += 1
  opponents.set(key, current)
}

function opponentKey(opponent) {
  return opponent.playerId ?? opponent.federatedPlayerId ?? opponent.playerSeasonId
    ?? `unavailable-${opponent.source ?? 'unknown'}-${opponent.season ?? 'unknown'}`
}

export function MatchOpponentDetails({ match }) {
  const games = match.games ?? []
  return <details className="opponent-match card">
    <summary>{match.competition} · {match.dateTime ? new Date(match.dateTime).toLocaleDateString('ca-ES') : 'Data no disponible'} · {scoreLabel(match)}</summary>
    <div className="opponent-match-content">
      <p><strong>Resultat:</strong> {resultLabel(match.result)} · <strong>Equips:</strong> {match.homeTeam} — {match.awayTeam}</p>
      {games.length === 0 ? <p>Detall d’oponents no disponible.</p> : games.map((game) => (
        <div className="opponent-game" key={game.id}>
          <strong>Joc {game.gameNumber} · {game.type === 'DOUBLES' ? 'Dobles' : 'Individual'}</strong>
          <span> {resultLabel(game.result)} · {game.homeSetsWon == null || game.awaySetsWon == null ? 'Marcador no disponible' : `${game.homeSetsWon} — ${game.awaySetsWon}`}</span>
          <p>Oponents: {game.opponents.length === 0 ? 'No disponibles' : game.opponents.map((opponent) => opponent.available ? opponent.name : 'No disponible').join(', ')}</p>
          {game.unavailableReason ? <p>{game.unavailableReason}</p> : null}
        </div>
      ))}
    </div>
  </details>
}

function opponentName(match) {
  return match.playerTeam === match.homeTeam ? match.awayTeam : match.homeTeam
}

function resultLabel(result) {
  return result === 'win' ? 'Victòria' : result === 'loss' ? 'Derrota' : 'Empat'
}

function scoreLabel(match) {
  return match.homeGamesWon == null || match.awayGamesWon == null
    ? '—'
    : `${match.homeGamesWon} — ${match.awayGamesWon}`
}

function activateTab(event, view, update, key = 'view') {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    update(key, view)
  }
}

function ConnectedScatterPlot({ values }) {
  const width = 640
  const height = 220
  const padding = { top: 16, right: 18, bottom: 36, left: 36 }
  const plotWidth = width - padding.left - padding.right
  const plotHeight = height - padding.top - padding.bottom
  const maxMatches = Math.max(...values.map((item) => item.matchesPlayed), 1)
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
    aria-label="Dispersió connectada: sèries de partits jugats i percentatge de victòries de totes les temporades seleccionades en un únic gràfic">
    <svg viewBox={`0 0 ${width} ${height}`} role="presentation" focusable="false" preserveAspectRatio="xMidYMid meet">
      <line className="chart-grid-line" x1={padding.left} y1={padding.top + plotHeight} x2={width - padding.right} y2={padding.top + plotHeight} />
      <polyline className="chart-line matches-line" fill="none" points={matchesPoints} />
      {winsPoints && <polyline className="chart-line wins-line" fill="none" points={winsPoints} />}
      {values.map((item, index) => <g key={`${item.source}-${item.season}-${index}`}>
        <circle className="chart-point matches-point" cx={x(index)} cy={yMatches(item.matchesPlayed)} r="3" />
        {item.winPercentage != null && <circle className="chart-point wins-point" cx={x(index)} cy={yWins(item.winPercentage)} r="3" />}
        <text className="chart-season-label" x={x(index)} y={height - 18} textAnchor="middle">{item.season || '—'}</text>
      </g>)}
      <text className="chart-axis-label" x="10" y={padding.top + plotHeight / 2} textAnchor="middle" transform={`rotate(-90 10 ${padding.top + plotHeight / 2})`}>Valor</text>
      <text className="chart-axis-label" x={width / 2} y={height - 2} textAnchor="middle">Temporades</text>
    </svg>
  </div>
}

function aggregateCompetition(matches, competition) {
  const selected = matches.filter((item) => item.competition === competition)
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
