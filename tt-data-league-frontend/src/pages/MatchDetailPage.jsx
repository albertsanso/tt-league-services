import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { getMatchDetails } from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'

function MatchDetailPage() {
  const { matchId } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ loading: true, data: null, error: null })
  useEffect(() => {
    const controller = new AbortController()
    getMatchDetails(matchId, token, controller.signal, clearSession)
      .then((data) => setState({ loading: false, data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setState({ loading: false, data: null, error })
      })
    return () => controller.abort()
  }, [clearSession, matchId, token])
  if (state.loading) return <p role="status">{t('matchesPage.loading')}</p>
  if (state.error) return <p role="alert">{state.error.status === 401 ? t('matchesPage.unauthorized') : t('matchesPage.error')}</p>
  const match = state.data
  return (
    <section className="page-block">
      <button type="button" onClick={() => navigate(-1)}>{t('matchesPage.back')}</button>
      <h1 className="page-title">{match.homeTeam?.name} – {match.awayTeam?.name}</h1>
      <p>{match.dateTime ? new Date(match.dateTime).toLocaleString() : t('common.unavailable')} · {match.competition}</p>
      <dl>
        <dt>{t('matchesPage.source')}</dt><dd>{match.source ?? t('common.unavailable')}</dd>
        <dt>{t('matchesPage.season')}</dt><dd>{match.season ?? t('common.unavailable')}</dd>
        <dt>{t('matchesPage.round')}</dt><dd>{match.round}</dd>
        <dt>{t('matchesPage.group')}</dt><dd>{match.groupNumber}</dd>
        <dt>{t('matchesPage.venue')}</dt><dd>{[match.city, match.venue].filter(Boolean).join(' · ') || t('common.unavailable')}</dd>
        <dt>{t('matchesPage.winner')}</dt><dd>{match.winnerTeam?.name ?? t('common.unavailable')}</dd>
        <dt>{t('matchesPage.referee')}</dt>
        <dd>{match.refereeName ?? t('common.unavailable')}{match.refereeLicense ? ` (${match.refereeLicense})` : ''}</dd>
      </dl>
      <p>{t('common.score')}: {match.homeGamesWon ?? '—'} – {match.awayGamesWon ?? '—'} · {match.homeSetsWon ?? '—'} – {match.awaySetsWon ?? '—'}</p>
      {match.protested ? <p role="status">{t('matchesPage.protested')}</p> : null}
      <h2>{t('matchesPage.lineups')}</h2>
      <ul>{(match.lineups ?? []).map((lineup) => (
        <li key={lineup.id}>
          {lineup.team?.name}: {lineup.letter} · {lineup.position} · {lineup.player?.name} ({lineup.player?.license ?? '—'})
          {lineup.ranking == null ? '' : ` · ${lineup.ranking}`}
        </li>
      ))}</ul>
      <h2>{t('matchesPage.games')}</h2>
      <ol>{(match.games ?? []).map((game) => (
        <li key={game.id}>
          {game.type} · {game.crossover ?? ''} · {game.homePlayer?.name ?? '—'} – {game.awayPlayer?.name ?? '—'} ·
          {' '}{game.homeSetsWon ?? '—'}–{game.awaySetsWon ?? '—'} · {game.winnerSide ?? t('common.unavailable')}
          {game.notPlayed ? ` · ${game.reason ?? t('common.unavailable')}` : ''}
          <span> ({(game.sets ?? []).map((set) => `${set.homePoints}-${set.awayPoints}`).join(', ')})</span>
          {(game.doublesPlayers ?? []).length ? (
            <span> · {(game.doublesPlayers ?? []).map((player) => `${player.side}: ${player.player?.name ?? '—'}`).join(', ')}</span>
          ) : null}
        </li>
      ))}</ol>
      <Link to="/partits">{t('matchesPage.back')}</Link>
    </section>
  )
}

export default MatchDetailPage
