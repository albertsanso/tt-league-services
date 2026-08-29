import { ArrowLeft } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerDetails } from '../hooks/usePlayers.js'

const ALL = 'all'
const VIEWS = {
  STATISTICS: 'statistics',
  MATCHES: 'matches',
  OPPONENTS: 'opponents',
}
const CHART_TYPES = ['line', 'bar', 'connected-scatter']
const CHART_LABELS = {
  line: 'Línies',
  bar: 'Barres',
  'connected-scatter': 'Dispersió connectada',
}
const unique = (values) => [...new Set(values.filter(Boolean))].sort()

function PlayerDetailPage() {
  const { playerId } = useParams()
  const [params, setParams] = useSearchParams()
  const { data, loading, error, retry } = usePlayerDetails(playerId)
  if (loading) return <p className="club-state card" role="status">Carregant el jugador...</p>
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
  return <PlayerDetailContent data={data} params={params} setParams={setParams} />
}

function PlayerDetailContent({ data, params, setParams }) {
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
    ...data.matches.filter((item) => !source || item.source === source).map((item) => item.season),
    ...data.statistics.filter((item) => !source || item.source === source).map((item) => item.season),
  ]).filter((item) => item !== '—')
  const selectedSeason = params.get('season')
  const season = selectedSeason === ALL ? '' : seasons.includes(selectedSeason) ? selectedSeason : ''
  const availableCompetitions = unique(data.competitions
    .filter((item) => (!source || item.source === source) && (!season || item.season === season))
    .map((item) => item.name))
  const selectedCompetition = params.get('competition')
  const competition = availableCompetitions.includes(selectedCompetition) ? selectedCompetition : ''
  const requestedChart = params.get('chart')
  const chart = CHART_TYPES.includes(requestedChart) ? requestedChart : 'line'
  const matches = data.matches.filter((item) => (
    (!source || item.source === source) && (!season || item.season === season)
      && (!competition || item.competition === competition)
  ))
  const clubs = data.clubs.filter((item) => (!source || item.source === source) && (!season || item.season === season))
  const federatedPlayers = data.federatedPlayers.filter((item) => !source || item.source === source)
  const statistics = data.statistics.filter((item) => (
    (!source || item.source === source) && (!season || item.season === season)
  ))

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
        <fieldset className="club-filter source-options"><legend>Font</legend>
          <label><input type="radio" name="player-source" checked={!source} onChange={() => update('source', ALL)} /> Totes les fonts</label>
          {sources.map((item) => <label key={item}><input type="radio" name="player-source" value={item} checked={source === item} onChange={() => update('source', item)} /> {item}</label>)}
        </fieldset>
        <fieldset className="club-filter season-slider"><legend>Temporada</legend>
          <output htmlFor="player-season">{season || 'Totes les temporades'}</output>
          <input id="player-season" type="range" min="0" max={Math.max(seasons.length, 0)} step="1"
            value={season ? seasons.indexOf(season) : seasons.length}
            onChange={(event) => update('season', seasons[Number(event.target.value)] ?? ALL)}
            disabled={seasons.length === 0}
            aria-label="Selecciona la temporada" />
          <button className="filter-all-button" type="button" onClick={() => update('season', ALL)}>Totes les temporades</button>
        </fieldset>
        <label className="club-filter"><span>Competició</span><select value={competition} onChange={(event) => update('competition', event.target.value)}>
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
        {view === VIEWS.MATCHES ? <MatchHistoryPanel matches={matches} /> : null}
        {view === VIEWS.OPPONENTS ? <OpponentAnalysisPanel matches={matches} /> : null}
      </div>
      <DetailList title="Registres federats" items={federatedPlayers} empty="No hi ha registres federats per als filtres seleccionats." render={(item) => `${item.name} · ${item.source}${item.license ? ` · Llicència: ${item.license}` : ''}`} />
      <DetailList title="Inscripcions per temporada" items={registrations.filter((item) => !season || item.season === season)} empty="No hi ha inscripcions per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source} · Llicència: ${item.license ?? '—'}`} />
      <DetailList title="Clubs associats" items={clubs} empty="No hi ha clubs associats per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source}`} />
      <DetailList title="Competicions" items={data.competitions.filter((item) => (!source || item.source === source) && (!season || item.season === season) && (!competition || item.name === competition))} empty="No hi ha competicions per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source} · ${item.matchCount} partits`} />
    </section>
  )
}

function HistorySection({ statistics, competition, matches, chart, update }) {
  const values = competition ? aggregateCompetition(matches, competition) : statistics
  return <section className="club-detail-section" aria-labelledby="player-history-title">
    <h2 id="player-history-title">Historial estadístic</h2>
    <label className="chart-type-selector">Tipus de gràfic
      <select value={chart} onChange={(event) => update('chart', event.target.value)}>
        {CHART_TYPES.map((type) => <option key={type} value={type}>{CHART_LABELS[type]}</option>)}
      </select>
    </label>
    {values.length === 0 ? <p className="club-empty card" role="status">No hi ha dades estadístiques disponibles per als filtres seleccionats.</p> : (
      <>
        {chart === 'connected-scatter' ? <ConnectedScatterPlot values={values} /> : (
          <div className={`history-chart card chart-${chart}`} role="img" aria-label={`${CHART_LABELS[chart]} de partits i percentatge de victòries per temporada`}>
            <span className="chart-axis chart-axis-y">Valor (partits, %)</span>
            {values.map((item, index) => <div className="history-bar-group" key={`${item.source}-${item.season}-${index}`}>
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
  return <section className="club-detail-section" aria-labelledby="player-matches-title">
    <h2 id="player-matches-title">Partits</h2>
    {matches.length === 0 ? <p className="club-empty card">No hi ha partits per als filtres seleccionats.</p> : (
      <div className="table-wrap">
        <table className="history-table">
          <caption>Historial de partits</caption>
          <thead><tr><th>Data</th><th>Font</th><th>Temporada</th><th>Competició</th><th>Oponent</th><th>Resultat</th><th>Marcador</th></tr></thead>
          <tbody>{matches.map((item) => <tr key={item.id}>
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
    )}
  </section>
}

function OpponentAnalysisPanel({ matches }) {
  const opponents = new Map()
  matches.forEach((match) => {
    const opponent = opponentName(match)
    const current = opponents.get(opponent) ?? { opponent, matches: 0, wins: 0, draws: 0, losses: 0 }
    current.matches += 1
    if (match.result === 'win') current.wins += 1
    if (match.result === 'loss') current.losses += 1
    if (match.result === 'draw') current.draws += 1
    opponents.set(opponent, current)
  })
  const rows = [...opponents.values()].sort((left, right) => (
    left.opponent.localeCompare(right.opponent, 'ca', { sensitivity: 'base' })
      || left.opponent.localeCompare(right.opponent, 'ca')
  ))
  return <section className="club-detail-section" aria-labelledby="player-opponents-title">
    <h2 id="player-opponents-title">Anàlisi d'oponents</h2>
    {rows.length === 0 ? <p className="club-empty card">No hi ha dades d'oponents per als filtres seleccionats.</p> : (
      <div className="table-wrap">
        <table className="history-table">
          <caption>Resultats per equip oponent</caption>
          <thead><tr><th>Oponent</th><th>Partits jugats</th><th>Victòries</th><th>Empats</th><th>Derrotes</th><th>Victòries %</th></tr></thead>
          <tbody>{rows.map((item) => {
            const decided = item.wins + item.losses
            return <tr key={item.opponent}><td>{item.opponent}</td><td>{item.matches}</td><td>{item.wins}</td><td>{item.draws}</td><td>{item.losses}</td><td>{decided === 0 ? '—' : `${(item.wins * 100 / decided).toFixed(1)}%`}</td></tr>
          })}</tbody>
        </table>
      </div>
    )}
  </section>
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

function activateTab(event, view, update) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    update('view', view)
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

function DetailList({ title, items, empty, render }) {
  return <section className="club-detail-section" aria-labelledby={`${title}-title`}>
    <h2 id={`${title}-title`}>{title}</h2>
    {items.length === 0 ? <p className="club-empty card">{empty}</p> : (
      <ul className="club-player-list" aria-label={title}>{items.map((item, index) => <li className="club-player-card card" key={item.id ?? `${title}-${index}`}><strong>{render(item)}</strong></li>)}</ul>
    )}
  </section>
}

export default PlayerDetailPage
