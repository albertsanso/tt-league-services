import { ArrowLeft, Edit3, Swords, Users } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useClubDetails } from '../hooks/useClubs.js'
import { useAuth } from '../context/useAuth.js'

function ClubDetailPage() {
  const { clubId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { hasRole } = useAuth()
  const { data: club, loading, error, retry } = useClubDetails(clubId)
  const isAdmin = hasRole('ADMIN')
  const isNotFound = error?.status === 404 || error?.status === 400
  const successMessage = location.state?.successMessage

  function goBack() {
    navigate('/clubs')
  }

  if (loading) {
    return <p className="club-state card" role="status" aria-live="polite">Carregant el club...</p>
  }

  if (isNotFound) {
    return (
      <section className="page-block" role="alert" aria-labelledby="club-not-found-title">
        <p className="section-label">Clubs</p>
        <h1 id="club-not-found-title" className="page-title">Club no trobat</h1>
        <p className="page-description">El club sol·licitat no existeix o l’identificador no és vàlid.</p>
        <button className="secondary-button" type="button" onClick={goBack}>
          <ArrowLeft size={16} aria-hidden="true" /> Torna a la cerca
        </button>
      </section>
    )
  }

  if (error) {
    return (
      <section className="page-block" role="alert" aria-labelledby="club-error-title">
        <h1 id="club-error-title" className="page-title">No s’ha pogut carregar el club</h1>
        <p className="page-description">Hi ha hagut un problema en consultar aquesta informació.</p>
        <button className="secondary-button" type="button" onClick={retry}>Reintenta</button>
      </section>
    )
  }

  return (
    <section className="page-block club-detail-page" aria-labelledby="club-detail-title">
      {successMessage ? <p className="form-success" role="status">{successMessage}</p> : null}
      <div className="club-detail-header">
        <div>
          <p className="section-label">Identitat del club</p>
          <h1 id="club-detail-title" className="page-title">{club.name}</h1>
          <p className="club-source">Font: {club.source}</p>
        </div>
        {isAdmin ? (
          <Link className="secondary-button" to={routePaths.clubEdit(club.id)}>
            <Edit3 size={16} aria-hidden="true" /> Edita el club
          </Link>
        ) : null}
      </div>

      <div className="club-action-row" aria-label="Accions del club">
        <Link className="secondary-button" to={routePaths.players(club.id)}>
          <Users size={16} aria-hidden="true" /> Jugadors
        </Link>
        <Link className="secondary-button" to={routePaths.matches(club.id)}>
          <Swords size={16} aria-hidden="true" /> Partits
        </Link>
      </div>

      <section className="club-detail-section" aria-labelledby="club-teams-title">
        <h2 id="club-teams-title">Equips i inscripcions</h2>
        {club.teams.length === 0 ? (
          <p className="club-empty">No hi ha equips registrats.</p>
        ) : (
          <ul className="club-team-list">
            {club.teams.map((team) => (
              <li key={`${team.id}-${team.season}`} className="club-team-card card">
                <strong>{team.name}</strong>
                <span>{team.season} · {team.source}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="club-detail-section" aria-labelledby="club-competitions-title">
        <h2 id="club-competitions-title">Competicions</h2>
        {club.competitions.length === 0 ? (
          <p className="club-empty">No hi ha resums de competició disponibles.</p>
        ) : (
          <ul className="club-competition-list">
            {club.competitions.map((competition) => (
              <li key={`${competition.name}-${competition.season}`} className="club-competition-card card">
                <div>
                  <strong>{competition.name}</strong>
                  <span>{competition.season}</span>
                </div>
                <span>
                  {competition.matchCount} partits disponibles · Resultats:{' '}
                  {competition.resultTotals.wins ?? 0} victòries,{' '}
                  {competition.resultTotals.draws ?? 0} empats,{' '}
                  {competition.resultTotals.losses ?? 0} derrotes
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  )
}

export default ClubDetailPage
