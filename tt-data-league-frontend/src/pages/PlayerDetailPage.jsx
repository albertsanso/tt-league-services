import { ArrowLeft } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerDetails } from '../hooks/usePlayers.js'

const ALL = 'all'
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
  const sources = unique(data.federatedPlayers.map((item) => item.source))
  const selectedSource = params.get('source')
  const source = selectedSource && sources.includes(selectedSource) ? selectedSource : ''
  const registrations = data.registrations.filter((item) => !source || item.source === source)
  const seasons = unique(registrations.map((item) => item.season))
  const selectedSeason = params.get('season')
  const season = selectedSeason === ALL ? '' : seasons.includes(selectedSeason) ? selectedSeason : ''
  const availableCompetitions = unique(data.competitions
    .filter((item) => (!source || item.source === source) && (!season || item.season === season))
    .map((item) => item.name))
  const selectedCompetition = params.get('competition')
  const competition = availableCompetitions.includes(selectedCompetition) ? selectedCompetition : ''
  const matches = data.matches.filter((item) => (
    (!source || item.source === source) && (!season || item.season === season)
      && (!competition || item.competition === competition)
  ))
  const clubs = data.clubs.filter((item) => (!source || item.source === source) && (!season || item.season === season))
  const federatedPlayers = data.federatedPlayers.filter((item) => !source || item.source === source)

  function update(key, value) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key === 'source') {
      next.delete('season')
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
      <div className="club-filters">
        <label className="club-filter"><span>Font</span><select value={source || ALL} onChange={(event) => update('source', event.target.value === ALL ? '' : event.target.value)}>
          <option value={ALL}>Totes les fonts</option>{sources.map((item) => <option key={item} value={item}>{item}</option>)}
        </select></label>
        <label className="club-filter"><span>Temporada</span><select value={season || ALL} onChange={(event) => update('season', event.target.value === ALL ? '' : event.target.value)}>
          <option value={ALL}>Totes les temporades</option>{seasons.map((item) => <option key={item} value={item}>{item}</option>)}
        </select></label>
        <label className="club-filter"><span>Competició</span><select value={competition} onChange={(event) => update('competition', event.target.value)}>
          <option value="">Totes les competicions</option>{availableCompetitions.map((item) => <option key={item} value={item}>{item}</option>)}
        </select></label>
      </div>
      <DetailList title="Registres federats" items={federatedPlayers} empty="No hi ha registres federats per als filtres seleccionats." render={(item) => `${item.name} · ${item.source}${item.license ? ` · Llicència: ${item.license}` : ''}`} />
      <DetailList title="Inscripcions per temporada" items={registrations.filter((item) => !season || item.season === season)} empty="No hi ha inscripcions per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source} · Llicència: ${item.license ?? '—'}`} />
      <DetailList title="Clubs associats" items={clubs} empty="No hi ha clubs associats per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source}`} />
      <DetailList title="Competicions" items={data.competitions.filter((item) => (!source || item.source === source) && (!season || item.season === season) && (!competition || item.name === competition))} empty="No hi ha competicions per als filtres seleccionats." render={(item) => `${item.name} · ${item.season} · ${item.source} · ${item.matchCount} partits`} />
      <DetailList title="Partits" items={matches} empty="No hi ha partits per als filtres seleccionats." render={(item) => `${item.homeTeam} — ${item.awayTeam} · ${item.competition} · Jornada ${item.round}`} />
    </section>
  )
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
