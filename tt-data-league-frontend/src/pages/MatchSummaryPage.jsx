import { ArrowLeft } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { routePaths } from '../config/routes.js'
import { useMatchSummary } from '../hooks/useMatches.js'
import MatchActaDialog from '../components/matches/MatchActaDialog.jsx'
import {
  computeAlignmentBadge,
  computeComparisonNote,
  computeTrendNote,
  formatFormStrip,
  formatRecord,
} from '../utils/matchSummary.js'

function backHref(params) {
  const query = params.toString()
  return query ? `${routePaths.matches()}?${query}` : routePaths.matches()
}

function TeamName({ team, returnSearch, t }) {
  if (!team) return t('common.unavailable')
  if (!team.clubId) return team.name
  return <Link to={routePaths.clubDetails(team.clubId, returnSearch)}>{team.name}</Link>
}

function CompetitionLabel({ match, returnSearch }) {
  const clubId = match.homeTeam?.clubId
  if (!clubId || !match.season || !match.competition) return match.competition
  return (
    <Link to={routePaths.clubCompetitionDetails(clubId, match.season, match.competition, returnSearch)}>
      {match.competition}
    </Link>
  )
}

function resultClass(result) {
  return result === 'win' ? 'win' : result === 'loss' ? 'loss' : result === 'draw' ? 'draw' : 'unknown'
}

function formatWinRate(value) {
  return value == null ? '—' : `${Math.round(value)}%`
}

function MatchSummaryPage() {
  const { matchId } = useParams()
  const [params] = useSearchParams()
  const { t } = useTranslation()
  const { data: match, loading, error, retry } = useMatchSummary(matchId)
  const [actaOpen, setActaOpen] = useState(false)

  if (loading && !match) {
    return <p className="club-state card" role="status" aria-live="polite">{t('detail.loadingClub')}</p>
  }

  if (error?.status === 404 || error?.status === 400) {
    return (
      <section className="page-block" role="alert">
        <h1 className="page-title">{t('matchSummaryPage.notFound')}</h1>
        <p className="page-description">{t('matchSummaryPage.notFoundDescription')}</p>
        <Link className="secondary-button" to={backHref(params)}>{t('matchesPage.back')}</Link>
      </section>
    )
  }

  if (error || !match) {
    return (
      <section className="page-block" role="alert">
        <h1 className="page-title">{t('matchSummaryPage.loadError')}</h1>
        <p className="page-description">{t('matchSummaryPage.loadErrorDescription')}</p>
        <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
      </section>
    )
  }

  const hasActa = match.homeGamesWon != null && match.awayGamesWon != null
  const playerFormBySeasonId = new Map((match.playerForm ?? []).map((form) => [form.playerSeasonId, form]))
  const homeLineups = (match.lineups ?? [])
    .filter((lineup) => lineup.team?.id === match.homeTeam?.id)
    .sort((left, right) => (left.letter ?? '').localeCompare(right.letter ?? ''))
  const awayLineups = (match.lineups ?? [])
    .filter((lineup) => lineup.team?.id === match.awayTeam?.id)
    .sort((left, right) => (left.letter ?? '').localeCompare(right.letter ?? ''))

  const viewActaButton = hasActa ? (
    <button type="button" className="secondary-button" onClick={() => setActaOpen(true)}>
      {t('matchesPage.viewActa')}
    </button>
  ) : null

  return (
    <section className="page-block match-summary-page" aria-labelledby="match-summary-title">
      <Link className="back-link" to={backHref(params)}>
        <ArrowLeft size={16} aria-hidden="true" /> {t('matchesPage.back')}
      </Link>
      <p className="section-label">{t('matchSummaryPage.matchLabel')}</p>
      <p className="match-summary-competition">
        <CompetitionLabel match={match} returnSearch={params} />
        {match.round != null ? ` · ${t('matchesPage.round')} ${match.round}` : ''}
        {match.dateTime ? ` · ${new Date(match.dateTime).toLocaleDateString()}` : ''}
      </p>
      <div className="match-summary-header">
        <h1 id="match-summary-title" className="page-title">
          <TeamName team={match.homeTeam} returnSearch={params} t={t} />
          {' '}<span className="match-summary-score">{match.homeGamesWon ?? '—'} – {match.awayGamesWon ?? '—'}</span>{' '}
          <TeamName team={match.awayTeam} returnSearch={params} t={t} />
        </h1>
        {viewActaButton}
      </div>
      <p className="match-summary-venue">
        {match.venue ?? t('common.unavailable')}
        {match.refereeName ? ` · ${t('matchesPage.referee')}: ${match.refereeName}` : ''}
      </p>

      <div className="match-summary-teams">
        <TeamPanel
          team={match.homeTeam}
          form={match.homeTeamForm}
          lineups={homeLineups}
          alignment={match.homeAlignmentStability}
          playerFormBySeasonId={playerFormBySeasonId}
          side="home"
          returnSearch={params}
          t={t}
        />
        <TeamPanel
          team={match.awayTeam}
          form={match.awayTeamForm}
          lineups={awayLineups}
          alignment={match.awayAlignmentStability}
          playerFormBySeasonId={playerFormBySeasonId}
          side="away"
          returnSearch={params}
          t={t}
        />
      </div>

      <div className="match-summary-footer">
        {viewActaButton}
        <Link className="back-link" to={backHref(params)}>
          <ArrowLeft size={16} aria-hidden="true" /> {t('matchesPage.back')}
        </Link>
      </div>

      {actaOpen ? <MatchActaDialog matchId={match.id} onClose={() => setActaOpen(false)} /> : null}
    </section>
  )
}

function TeamPanel({ team, form, lineups, alignment, playerFormBySeasonId, side, returnSearch, t }) {
  const results = form?.lastResults ?? []
  const record = formatRecord(results)
  const strip = formatFormStrip(results)
  const trend = computeTrendNote(form?.lastWinRate, form?.previousWinRate)
  const timesFielded = alignment?.timesFielded ?? 0
  const badge = computeAlignmentBadge(timesFielded)
  const comparison = computeComparisonNote(alignment?.winRate, alignment?.teamOverallWinRate)

  return (
    <article className={`match-summary-team match-summary-team-${side}`} aria-label={team?.name ?? t('common.unavailable')}>
      <section className="card match-summary-form-card">
        <h3><TeamName team={team} returnSearch={returnSearch} t={t} /></h3>
        {results.length === 0 ? (
          <p className="club-empty">{t('matchSummaryPage.formEmpty')}</p>
        ) : (
          <>
            <ul className="match-form-strip" aria-label={t('matchSummaryPage.recentForm')}>
              {strip.map((letter, index) => (
                <li key={results[index]?.matchId ?? index} className={`match-form-chip is-${resultClass(results[index]?.result)}`}>
                  {letter}
                </li>
              ))}
            </ul>
            <p className="match-form-record">
              {t('matchSummaryPage.lastRecord', {
                count: results.length,
                wins: record.wins,
                draws: record.draws,
                losses: record.losses,
                winRate: formatWinRate(form?.lastWinRate),
              })}
            </p>
            {trend ? (
              <p className="match-form-trend">
                {t(`matchSummaryPage.trend.${trend.tone}`, {
                  previous: Math.round(trend.previousWinRate),
                  current: Math.round(trend.currentWinRate),
                })}
              </p>
            ) : null}
          </>
        )}
      </section>

      <section className="card match-summary-lineup-card">
        <h3>{t('matchSummaryPage.lineup')}</h3>
        {lineups.length === 0 ? (
          <p className="club-empty">{t('matchSummaryPage.lineupEmpty')}</p>
        ) : (
          <div className="table-wrap">
            <table className="history-table match-lineup-table">
              <caption>{t('matchSummaryPage.lineup')}</caption>
              <thead>
                <tr>
                  <th>{t('matchSummaryPage.letter')}</th>
                  <th>{t('common.players')}</th>
                  <th>{t('matchSummaryPage.ranking')}</th>
                  <th>{t('matchSummaryPage.playerForm')}</th>
                </tr>
              </thead>
              <tbody>
                {lineups.map((lineup) => {
                  const playerForm = playerFormBySeasonId.get(lineup.player?.playerSeasonId)
                  const playerResults = playerForm?.lastResults ?? []
                  return (
                    <tr key={lineup.id}>
                      <td data-label={t('matchSummaryPage.letter')}>{lineup.letter}</td>
                      <td data-label={t('common.players')}>
                        {lineup.player?.canonicalPlayerId ? (
                          <Link
                            to={routePaths.playerDetails(lineup.player.canonicalPlayerId, returnSearch)}
                          >
                            {lineup.player.name}
                          </Link>
                        ) : (
                          lineup.player?.name ?? t('common.unavailable')
                        )}
                      </td>
                      <td data-label={t('matchSummaryPage.ranking')}>{lineup.ranking ?? '—'}</td>
                      <td data-label={t('matchSummaryPage.playerForm')}>
                        {playerResults.length === 0 ? (
                          t('matchSummaryPage.formEmpty')
                        ) : (
                          <>
                            {formatFormStrip(playerResults).join(' ')}
                            {' '}({formatWinRate(playerForm?.winRate)})
                          </>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card match-summary-alignment-card">
        <h3>{t('matchSummaryPage.alignmentStability')}</h3>
        <span className={`alignment-badge is-${badge}`}>{t(`matchSummaryPage.alignmentBadge.${badge}`)}</span>
        {badge === 'new' ? (
          <p className="match-alignment-note">{t('matchSummaryPage.alignmentNewCombination')}</p>
        ) : (
          <>
            <p className="match-alignment-stat">
              {t('matchSummaryPage.alignmentTimesFielded', { count: timesFielded })}
            </p>
            <p className="match-alignment-stat">
              {t('matchSummaryPage.alignmentRecord', {
                wins: alignment?.wins ?? 0,
                draws: alignment?.draws ?? 0,
                losses: alignment?.losses ?? 0,
                winRate: formatWinRate(alignment?.winRate),
              })}
            </p>
            {comparison ? (
              <p className="match-alignment-comparison">
                {t(`matchSummaryPage.comparison.${comparison}`, {
                  winRate: formatWinRate(alignment?.teamOverallWinRate),
                })}
              </p>
            ) : null}
          </>
        )}
      </section>
    </article>
  )
}

export default MatchSummaryPage
