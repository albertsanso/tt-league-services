import { ArrowLeft } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useClubCompetitionDetails } from '../hooks/useClubs.js'

function ClubCompetitionDetailPage() {
  const { clubId, season, competition } = useParams()
  const location = useLocation()
  const { data, loading, error, retry } = useClubCompetitionDetails(clubId, season, competition)
  const returnSearch = location.search || `?season=${encodeURIComponent(season)}&competition=${encodeURIComponent(competition)}`
  const clubPath = routePaths.clubDetails(clubId, returnSearch)

  if (loading) {
    return <p className="club-state card" role="status" aria-live="polite">Carregant la competició...</p>
  }

  if (error?.status === 404 || error?.status === 400) {
    return (
      <section className="page-block" role="alert" aria-labelledby="competition-not-found-title">
        <h1 id="competition-not-found-title" className="page-title">Competició no trobada</h1>
        <p className="page-description">No s’ha trobat la competició sol·licitada per a aquesta temporada.</p>
        <Link className="secondary-button" to={clubPath}>
          <ArrowLeft size={16} aria-hidden="true" /> Torna al club
        </Link>
      </section>
    )
  }

  if (error || !data) {
    return (
      <section className="page-block" role="alert" aria-labelledby="competition-error-title">
        <h1 id="competition-error-title" className="page-title">No s’ha pogut carregar la competició</h1>
        <p className="page-description">Hi ha hagut un problema en consultar els partits.</p>
        <button className="secondary-button" type="button" onClick={retry}>Reintenta</button>
      </section>
    )
  }

  return (
    <section className="page-block competition-detail-page" aria-labelledby="competition-detail-title">
      <Link className="back-link" to={clubPath}>
        <ArrowLeft size={16} aria-hidden="true" /> Torna al club
      </Link>
      <div>
        <p className="section-label">Competició</p>
        <h1 id="competition-detail-title" className="page-title">{data.competition}</h1>
        <p className="page-description">{data.clubName} · {data.season} · Font: {data.source}</p>
      </div>
      <section className="club-detail-section" aria-labelledby="competition-matches-title">
        <h2 id="competition-matches-title">Partits</h2>
        {data.matches.length === 0 ? (
          <p className="club-empty card">No hi ha partits disponibles per a aquesta competició.</p>
        ) : (
          <ul className="club-match-list" aria-label="Partits de la competició">
            {data.matches.map((match) => (
              <li key={match.id} className="club-match-card card">
                <div>
                  <strong>{match.homeTeam} — {match.awayTeam}</strong>
                  <span>Jornada {match.round}{match.venue ? ` · ${match.venue}` : ''}</span>
                </div>
                <div className={`club-match-result is-${match.result}`}>
                  <strong>
                    {match.homeGamesWon == null || match.awayGamesWon == null
                      ? 'Resultat pendent'
                      : `${match.homeGamesWon} — ${match.awayGamesWon}`}
                  </strong>
                  <span>{match.result === 'win' ? 'Victòria' : match.result === 'loss' ? 'Derrota' : 'Empat'}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  )
}

export default ClubCompetitionDetailPage
