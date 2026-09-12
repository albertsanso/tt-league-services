import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMatchDetails } from '../../api/matches.js'
import { useAuth } from '../../context/useAuth.js'

const SET_COUNT = 5

function splitCrossover(crossover) {
  if (!crossover) return [null, null]
  const [local, visitor] = crossover.split(/\s*vs\s*/i)
  return [local || null, visitor || null]
}

function findLineupRanking(lineups, player) {
  if (!player) return null
  const match = lineups.find((lineup) => (
    (player.playerSeasonId && lineup.player?.playerSeasonId === player.playerSeasonId)
    || (player.license && lineup.player?.license === player.license)
  ))
  return match?.ranking ?? null
}

function buildRows(match) {
  const lineups = match.lineups ?? []
  return [...(match.games ?? [])]
    .sort((a, b) => a.gameNumber - b.gameNumber)
    .map((game) => {
      const [posLocal, posCruce] = splitCrossover(game.crossover)
      const sets = Array.from({ length: SET_COUNT }, (_, index) => {
        const set = (game.sets ?? []).find((item) => item.setNumber === index + 1)
        return set ? `${set.homePoints}-${set.awayPoints}` : ''
      })
      return {
        id: game.id,
        posLocal,
        posCruce,
        homePlayer: game.homePlayer,
        awayPlayer: game.awayPlayer,
        homeRanking: findLineupRanking(lineups, game.homePlayer),
        awayRanking: findLineupRanking(lineups, game.awayPlayer),
        sets,
        homeSetsWon: game.homeSetsWon,
        awaySetsWon: game.awaySetsWon,
        winnerSide: game.winnerSide,
        cumulativeHomeSetsWon: game.cumulativeHomeSetsWon,
        cumulativeAwaySetsWon: game.cumulativeAwaySetsWon,
      }
    })
}

function computeTotals(match) {
  const games = match.games ?? []
  const gamesTotal = games.reduce((acc, game) => ({
    home: acc.home + (game.homeSetsWon ?? 0),
    away: acc.away + (game.awaySetsWon ?? 0),
  }), { home: 0, away: 0 })
  return { gamesTotal }
}

function MatchActaDialog({ matchId, onClose }) {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const dialogRef = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    getMatchDetails(matchId, token, controller.signal, clearSession)
      .then((data) => setState({ loading: false, data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setState({ loading: false, data: null, error })
      })
    return () => controller.abort()
  }, [clearSession, matchId, token])

  useEffect(() => {
    dialogRef.current?.focus()
    function handleKeyDown(event) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const match = state.data
  const hasActa = !state.loading && !state.error && Array.isArray(match?.games) && match.games.length > 0
  const rows = hasActa ? buildRows(match) : []
  const totals = hasActa ? computeTotals(match) : null

  return (
    <div className="acta-dialog-overlay" role="presentation" onMouseDown={onClose}>
      <div
        className="acta-dialog card"
        role="dialog"
        aria-modal="true"
        aria-label={t('matchesPage.viewActa')}
        tabIndex={-1}
        ref={dialogRef}
        onMouseDown={(event) => event.stopPropagation()}
      >
        {state.loading ? <p role="status">{t('matchesPage.loading')}</p> : null}
        {state.error ? (
          <p role="alert">{state.error.status === 401 ? t('matchesPage.unauthorized') : t('matchesPage.error')}</p>
        ) : null}
        {!state.loading && !state.error && !hasActa ? (
          <p role="status">{t('matchesPage.actaNoData')}</p>
        ) : null}
        {hasActa ? (
          <article className="acta-card">
            <div className="acta-jornada-bar">{t('matchesPage.actaJornada', { round: match.round })}</div>
            <div className="acta-score-header">
              <div className="acta-score-datetime">
                {match.dateTime ? (
                  <>
                    <span>{new Date(match.dateTime).toLocaleDateString()}</span>
                    <span>{new Date(match.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                  </>
                ) : <span>{t('common.unavailable')}</span>}
              </div>
              <div className="acta-score-teams">
                <span className="acta-team-name">{match.homeTeam?.name}</span>
                <span className="acta-score-badge">{match.homeGamesWon ?? '—'}</span>
                <span className="acta-score-badge">{match.awayGamesWon ?? '—'}</span>
                <span className="acta-team-name">{match.awayTeam?.name}</span>
              </div>
            </div>
            <div className="table-wrap">
              <table className="acta-table">
                <thead>
                  <tr>
                    <th colSpan={2}>{match.homeTeam?.name}</th>
                    <th colSpan={2}>{match.awayTeam?.name}</th>
                    <th>J1</th>
                    <th>J2</th>
                    <th>J3</th>
                    <th>J4</th>
                    <th>J5</th>
                    <th>{t('common.score')}</th>
                    <th>{t('matchesPage.actaGamesTotal')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id}>
                      <td className="acta-position-cell">{row.posLocal}</td>
                      <td className="acta-player-cell">
                        <div className="acta-player-meta">Lic: {row.homePlayer?.license ?? '—'} · Rk: {row.homeRanking ?? '—'}</div>
                        <strong>{row.homePlayer?.name ?? '—'}</strong>
                      </td>
                      <td className="acta-position-cell">{row.posCruce}</td>
                      <td className="acta-player-cell">
                        <div className="acta-player-meta">Lic: {row.awayPlayer?.license ?? '—'} · Rk: {row.awayRanking ?? '—'}</div>
                        <strong>{row.awayPlayer?.name ?? '—'}</strong>
                      </td>
                      {row.sets.map((set, index) => (
                        <td key={`${row.id}-set-${index}`}>{set}</td>
                      ))}
                      <td
                        className={
                          row.winnerSide === 'HOME' ? 'acta-parcial-home-win'
                            : row.winnerSide === 'AWAY' ? 'acta-parcial-away-win' : ''
                        }
                      >
                        {row.homeSetsWon ?? '—'}-{row.awaySetsWon ?? '—'}
                      </td>
                      <td>{row.cumulativeHomeSetsWon}-{row.cumulativeAwaySetsWon}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="acta-totals-row">
              <div>
                <span>{t('matchesPage.actaGamesTotal')}</span>
                <strong>{totals.gamesTotal.home} / {totals.gamesTotal.away}</strong>
              </div>
            </div>
            <p className="acta-footer-line">
              {t('matchesPage.actaVenue')}: {[match.venue, match.city].filter(Boolean).join(' - ') || t('common.unavailable')}
            </p>
            <p className="acta-footer-line">
              {t('matchesPage.actaReferee')}: {match.refereeName ?? t('common.unavailable')}
            </p>
          </article>
        ) : null}
      </div>
    </div>
  )
}

export default MatchActaDialog
