import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { updateClubName } from '../api/clubs.js'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails } from '../hooks/useClubs.js'
import { useTranslation } from 'react-i18next'

function ClubEditPage() {
  const { clubId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { token, clearSession } = useAuth()
  const { data: club, loading, error } = useClubDetails(clubId)
  const { t } = useTranslation()
  if (loading) {
    return <p className="club-state card" role="status">{t('detail.loadingClub')}</p>
  }

  if (error || !club) {
    return (
      <section className="page-block">
        <h1 className="page-title">{t('detail.clubLoadError')}</h1>
        <p className="page-description">{t('detail.updateError')}</p>
        <Link className="secondary-button" to={routePaths.clubDetails(clubId, location.search)}>
          {t('detail.backClub')}
        </Link>
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
  const { t } = useTranslation()
  const [validationError, setValidationError] = useState('')
  const [requestError, setRequestError] = useState(null)
  const [isSubmitting, setSubmitting] = useState(false)
  const controllerRef = useRef(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  async function handleSubmit(event) {
    event.preventDefault()
    const normalizedName = name.trim()
    if (normalizedName.length < 2) {
      setValidationError(t('detail.nameValidation'))
      return
    }

    setValidationError('')
    setRequestError(null)
    setSubmitting(true)
    const controller = new AbortController()
    controllerRef.current = controller
    try {
      await updateClubName(clubId, normalizedName, token, controller.signal, clearSession)
      navigate(routePaths.clubDetails(clubId, returnSearch), {
        replace: true,
        state: { successMessage: t('detail.updated') },
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
        <p className="section-label">{t('detail.admin')}</p>
        <h1 id="club-edit-title" className="page-title">{t('detail.editClub')}</h1>
        <p className="page-description">{t('detail.editDescription')}</p>
      </div>
      <form className="club-edit-form card" onSubmit={handleSubmit}>
        {validationError ? <p className="form-error" role="alert">{validationError}</p> : null}
        {requestError ? (
          <p className="form-error" role="alert">
            {requestError.status === 403
              ? t('detail.permissionEdit')
              : t('detail.updateError')}
          </p>
        ) : null}
        <label className="auth-field" htmlFor="club-name">
          {t('search.fieldClub')}
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
          <Link className="secondary-button" to={routePaths.clubDetails(clubId, returnSearch)}>{t('common.cancel')}</Link>
          <button className="primary-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? t('detail.saving') : t('detail.save')}
          </button>
        </div>
      </form>
    </section>
  )
}

export default ClubEditPage
