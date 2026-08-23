import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { updateClubName } from '../api/clubs.js'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails } from '../hooks/useClubs.js'

function ClubEditPage() {
  const { clubId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { token, clearSession } = useAuth()
  const { data: club, loading, error } = useClubDetails(clubId)
  if (loading) {
    return <p className="club-state card" role="status">Carregant el club...</p>
  }

  if (error || !club) {
    return (
      <section className="page-block">
        <h1 className="page-title">No s’ha pogut carregar el club</h1>
        <p className="page-description">No es pot editar aquest club en aquests moments.</p>
        <Link className="secondary-button" to={routePaths.clubDetails(clubId)}>Torna al club</Link>
      </section>
    )
  }

  return (
    <ClubEditForm
      club={club}
      clubId={clubId}
      token={token}
      clearSession={clearSession}
      navigate={navigate}
      returnSearch={location.search}
    />
  )
}

function ClubEditForm({ club, clubId, token, clearSession, navigate, returnSearch }) {
  const [name, setName] = useState(club.name)
  const [validationError, setValidationError] = useState('')
  const [requestError, setRequestError] = useState(null)
  const [isSubmitting, setSubmitting] = useState(false)
  const controllerRef = useRef(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  async function handleSubmit(event) {
    event.preventDefault()
    const normalizedName = name.trim()
    if (normalizedName.length < 2) {
      setValidationError('El nom del club necessita almenys 2 caràcters.')
      return
    }

    setValidationError('')
    setRequestError(null)
    setSubmitting(true)
    const controller = new AbortController()
    controllerRef.current = controller
    try {
      await updateClubName(clubId, normalizedName, token, controller.signal, clearSession)
      navigate(`${routePaths.clubDetails(clubId)}${returnSearch}`, {
        replace: true,
        state: { successMessage: 'El nom del club s’ha actualitzat correctament.' },
      })
    } catch (caughtError) {
      if (caughtError.name !== 'AbortError') {
        setRequestError(caughtError)
      }
    } finally {
      controllerRef.current = null
      setSubmitting(false)
    }
  }

  return (
    <section className="page-block" aria-labelledby="club-edit-title">
      <div>
        <p className="section-label">Administració</p>
        <h1 id="club-edit-title" className="page-title">Edita el club</h1>
        <p className="page-description">Actualitza el nom visible del club.</p>
      </div>
      <form className="club-edit-form card" onSubmit={handleSubmit}>
        {validationError ? <p className="form-error" role="alert">{validationError}</p> : null}
        {requestError ? (
          <p className="form-error" role="alert">
            {requestError.status === 403
              ? 'No tens permisos per editar aquest club.'
              : 'No s’ha pogut actualitzar el club. Torna-ho a provar.'}
          </p>
        ) : null}
        <label className="auth-field" htmlFor="club-name">
          Nom del club
          <input
            id="club-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            minLength={2}
            maxLength={255}
            required
            autoComplete="organization"
          />
        </label>
        <div className="club-form-actions">
          <Link className="secondary-button" to={`${routePaths.clubDetails(clubId)}${returnSearch}`}>Cancel·la</Link>
          <button className="primary-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Desant...' : 'Desa els canvis'}
          </button>
        </div>
      </form>
    </section>
  )
}

export default ClubEditPage
