import { ArrowLeft } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useClubCompetitionDetails } from '../hooks/useClubs.js'
import { useTranslation } from 'react-i18next'

function ClubCompetitionDetailPage() {
  const { clubId, season, competition } = useParams()
  const location = useLocation()
  const { data, loading, error, retry } = useClubCompetitionDetails(clubId, season, competition)
  const { t } = useTranslation()
  const returnSearch = location.search || `?season=${encodeURIComponent(season)}&competition=${encodeURIComponent(competition)}`
  const clubPath = routePaths.clubDetails(clubId, returnSearch)

  if (loading) {
    return <p className="club-state card" role="status" aria-live="polite">{t('detail.loadingCompetition')}</p>
  }

  if (error?.status === 404 || error?.status === 400) {
    return (
      <section className="page-block" role="alert" aria-labelledby="competition-not-found-title">
        <h1 id="competition-not-found-title" className="page-title">{t('detail.competitionNotFound')}</h1>
        <p className="page-description">{t('detail.competitionNotFoundDescription')}</p>
        <Link className="secondary-button" to={clubPath}>
          <ArrowLeft size={16} aria-hidden="true" /> {t('detail.backClub')}
        </Link>
      </section>
    )
  }

  if (error || !data) {
    return (
      <section className="page-block" role="alert" aria-labelledby="competition-error-title">
        <h1 id="competition-error-title" className="page-title">{t('detail.competitionLoadError')}</h1>
        <p className="page-description">{t('detail.matchesQueryError')}</p>
        <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
      </section>
    )
  }

  return (
    <section className="page-block competition-detail-page" aria-labelledby="competition-detail-title">
      <Link className="back-link" to={clubPath}>
        <ArrowLeft size={16} aria-hidden="true" /> {t('detail.backClub')}
      </Link>
      <div>
        <p className="section-label">{t('detail.competition')}</p>
        <h1 id="competition-detail-title" className="page-title">{data.competition}</h1>
        <p className="page-description">{data.clubName} · {data.season} · {t('common.sourceLabel', { source: data.source })}</p>
      </div>
      <section className="club-detail-section" aria-labelledby="competition-matches-title">
        <h2 id="competition-matches-title">{t('common.matches')}</h2>
        {data.matches.length === 0 ? (
          <p className="club-empty card">{t('detail.competitionEmpty')}</p>
        ) : (
          <ul className="club-match-list" aria-label={t('detail.competitionMatchesLabel')}>
            {data.matches.map((match) => (
              <li key={match.id} className="club-match-card card">
                <div>
                  <strong>{match.homeTeam} — {match.awayTeam}</strong>
                  <span>{t('detail.round', { round: match.round })}{match.venue ? ` · ${match.venue}` : ''}</span>
                </div>
                <div className={`club-match-result is-${match.result}`}>
                  <strong>
                    {match.homeGamesWon == null || match.awayGamesWon == null
                      ? t('detail.pendingResult')
                      : `${match.homeGamesWon} — ${match.awayGamesWon}`}
                  </strong>
                  <span>{match.result === 'win' ? t('detail.win') : match.result === 'loss' ? t('detail.loss') : t('detail.draw')}</span>
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
