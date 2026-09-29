import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { clearMatchOverdueMark, getMatchOptions, markMatchOverdue } from '../api/matches.js'
import { useSeasonCalendar } from '../hooks/useMatches.js'
import { useAuth } from '../context/useAuth.js'
import { routePaths } from '../config/routes.js'
import Badge from '../components/ui/Badge.jsx'
import Button from '../components/ui/Button.jsx'
import Card from '../components/ui/Card.jsx'
import ProgressBar from '../components/ui/ProgressBar.jsx'

const sources = ['RFETM', 'FCTT', 'BCNESA']

const STATE_TONES = {
  PLAYED: 'success',
  UPCOMING: 'subtle',
  AWAITING_RESULT: 'warning',
  UNDATED: 'subtle',
  OVERDUE: 'error',
  POSTPONED: 'warning',
}

function stateLabel(t, state) {
  return t(`calendarPage.states.${state}`)
}

function formatDateTime(value, fallback) {
  if (!value) return fallback
  return new Date(value).toLocaleString()
}

function formatDate(value) {
  if (!value) return null
  return new Date(value).toLocaleDateString()
}

function roundDateRange(round) {
  if (!round.firstDate && !round.lastDate) return null
  const first = formatDate(round.firstDate)
  const last = formatDate(round.lastDate)
  if (first === last) return first
  return [first, last].filter(Boolean).join(' – ')
}

function SeasonCalendarPage() {
  const { t } = useTranslation()
  const { token, clearSession, hasPermission } = useAuth()
  const [params, setParams] = useSearchParams()

  const filters = useMemo(() => ({
    source: params.get('source') ?? '',
    season: params.get('season') ?? '',
    competition: params.get('competition') ?? '',
    group: params.get('group') ?? '',
    round: params.get('round') ?? '',
  }), [params])

  const [options, setOptions] = useState({ seasons: [], competitions: [] })
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [optionsError, setOptionsError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState(null)

  const { data, loading, error, retry } = useSeasonCalendar(filters)

  useEffect(() => {
    if (!filters.source) {
      return undefined
    }
    const controller = new AbortController()
    Promise.resolve().then(() => {
      if (!controller.signal.aborted) setLoadingOptions(true)
      return getMatchOptions(filters.source, filters.season, token, controller.signal, clearSession)
    })
      .then((value) => {
        if (controller.signal.aborted) return
        setOptions({
          seasons: Array.isArray(value.seasons) ? value.seasons : [],
          competitions: Array.isArray(value.competitions) ? value.competitions : [],
        })
      })
      .catch((requestError) => {
        if (requestError.name !== 'AbortError' && !controller.signal.aborted) setOptionsError(requestError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingOptions(false)
      })
    return () => controller.abort()
  }, [clearSession, filters.season, filters.source, token])

  const update = (key, value) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key === 'source') {
      next.delete('season')
      next.delete('competition')
      next.delete('group')
      next.delete('round')
      setOptions({ seasons: [], competitions: [] })
    } else if (key === 'season') {
      next.delete('competition')
      next.delete('group')
      next.delete('round')
    } else if (key === 'competition') {
      next.delete('group')
      next.delete('round')
    }
    setActionError(null)
    setParams(next)
  }

  const canLoad = Boolean(filters.source && filters.season && filters.competition)
  const groups = useMemo(() => data?.groups ?? [], [data])
  const groupOptions = useMemo(() => {
    const values = groups.map((group) => group.groupNumber).filter((value) => value != null)
    return [...new Set(values)].sort((a, b) => a - b)
  }, [groups])
  const roundOptions = useMemo(() => {
    const values = groups.flatMap((group) => group.rounds.map((round) => round.round))
    return [...new Set(values)].sort((a, b) => a - b)
  }, [groups])

  const toggleMark = async (match) => {
    if (busyId) return
    setBusyId(match.id)
    setActionError(null)
    try {
      if (match.overdueMarked) {
        await clearMatchOverdueMark(match.id, token, undefined, clearSession)
      } else {
        await markMatchOverdue(match.id, token, undefined, clearSession)
      }
      retry()
    } catch (requestError) {
      if (requestError.name !== 'AbortError') setActionError(requestError)
    } finally {
      setBusyId(null)
    }
  }

  const markName = (match) => `${match.homeTeamName} – ${match.awayTeamName}`

  return (
    <section className="page-block">
      <h1 className="page-title">{t('calendarPage.title')}</h1>
      <p className="page-description">{t('calendarPage.description')}</p>

      <article className="card match-filter-card">
        <h2>{t('calendarPage.filters')}</h2>
        <div className="match-filter-grid">
          <label className="match-filter-field">
            {t('calendarPage.source')}
            <select value={filters.source} onChange={(event) => update('source', event.target.value)}>
              <option value="">{t('calendarPage.allSources')}</option>
              {sources.map((source) => <option key={source} value={source}>{source}</option>)}
            </select>
          </label>
          <label className="match-filter-field">
            {t('calendarPage.season')}
            <select
              value={filters.season}
              disabled={!filters.source || loadingOptions}
              onChange={(event) => update('season', event.target.value)}
            >
              <option value="">{t('calendarPage.select')}</option>
              {options.seasons.map((season) => <option key={season} value={season}>{season}</option>)}
            </select>
          </label>
          <label className="match-filter-field">
            {t('calendarPage.competition')}
            <select
              value={filters.competition}
              disabled={!filters.season || loadingOptions}
              onChange={(event) => update('competition', event.target.value)}
            >
              <option value="">{t('calendarPage.select')}</option>
              {options.competitions.map((competition) => (
                <option key={competition} value={competition}>{competition}</option>
              ))}
            </select>
          </label>
          <label className="match-filter-field">
            {t('calendarPage.group')}
            <select
              value={filters.group}
              disabled={!canLoad}
              onChange={(event) => update('group', event.target.value)}
            >
              <option value="">{t('calendarPage.allGroups')}</option>
              {groupOptions.map((group) => <option key={group} value={group}>{group}</option>)}
            </select>
          </label>
          <label className="match-filter-field">
            {t('calendarPage.round')}
            <select
              value={filters.round}
              disabled={!canLoad}
              onChange={(event) => update('round', event.target.value)}
            >
              <option value="">{t('calendarPage.allRounds')}</option>
              {roundOptions.map((round) => <option key={round} value={round}>{round}</option>)}
            </select>
          </label>
        </div>
      </article>

      {actionError ? <p role="alert">{actionError.status === 409 ? t('calendarPage.markConflict') : t('calendarPage.markError')}</p> : null}
      {optionsError && !data ? <p role="alert">{t('calendarPage.error')}</p> : null}
      {error ? <p role="alert">{error.status === 401 ? t('calendarPage.unauthorized') : t('calendarPage.error')}</p> : null}
      {canLoad && loading ? <p role="status">{t('calendarPage.loading')}</p> : null}
      {canLoad && !loading && groups.length === 0 && !error ? <p role="status">{t('calendarPage.empty')}</p> : null}

      {data?.overdueGraceDays != null ? (
        <p className="page-description">{t('calendarPage.legend', { days: data.overdueGraceDays })}</p>
      ) : null}

      {groups.map((group) => {
        const key = `${group.groupNumber ?? ''}-${group.phase ?? ''}`
        const total = group.scheduledMatches + group.playedMatches
        const progress = total > 0 ? (group.playedMatches / total) * 100 : null
        return (
          <Card key={key} className="calendar-group-card">
            <header>
              <h2>
                {group.groupNumber != null
                  ? t('calendarPage.groupLabel', { group: group.groupNumber })
                  : t('calendarPage.competition')}
                {group.phase ? <> · {group.phase}</> : null}
              </h2>
              <div className="calendar-group-meta">
                <span>{t('calendarPage.currentRound')}: {group.currentRound ?? t('calendarPage.none')}</span>
                <span>{t('calendarPage.lastCompleteRound')}: {group.lastCompleteRound ?? t('calendarPage.none')}</span>
              </div>
              <div className="calendar-group-counts">
                <span>{t('calendarPage.scheduled')}: {group.scheduledMatches}</span>
                <span>{t('calendarPage.played')}: {group.playedMatches}</span>
                <span>{t('calendarPage.overdue')}: {group.overdueMatches}</span>
                <span>{t('calendarPage.postponed')}: {group.postponedMatches}</span>
              </div>
              <ProgressBar
                value={progress}
                label={t('calendarPage.progress')}
                valueText={`${group.playedMatches} / ${total}`}
              />
            </header>

            <ol className="calendar-rounds" aria-label={t('calendarPage.filters')}>
              {group.rounds.map((round) => {
                const dates = roundDateRange(round)
                return (
                  <li key={round.round} className="calendar-round">
                    <h3>
                      {t('matchesPage.round')} {round.round}
                      {dates ? <span className="club-source">{dates}</span> : null}
                      {round.complete ? <Badge tone="success">{t('calendarPage.complete')}</Badge> : null}
                      {round.current ? <Badge tone="subtle">{t('calendarPage.current')}</Badge> : null}
                    </h3>
                    <ul className="club-result-list">
                      {round.matches.map((match) => {
                        const teamLabel = markName(match)
                        const markable = hasPermission('matches:write') && match.status === 'SCHEDULED'
                        const manual = match.overdueMarked
                        return (
                          <li key={match.id} className="club-result card">
                            {match.status === 'PLAYED' ? (
                              <Link className="club-result-link" to={routePaths.matchSummary(match.id, params)}>
                                <span>
                                  <strong>{match.homeTeamName} – {match.awayTeamName}</strong>
                                  <span className="club-source">
                                    {formatDateTime(match.dateTime, t('common.unavailable'))}
                                    {match.venue ? ` · ${match.venue}` : ''}
                                  </span>
                                  <span className="club-source">{match.homeGamesWon ?? '—'}–{match.awayGamesWon ?? '—'}</span>
                                </span>
                              </Link>
                            ) : (
                              <span className="calendar-match-row">
                                <strong>{match.homeTeamName} – {match.awayTeamName}</strong>
                                <span className="club-source">
                                  {formatDateTime(match.dateTime, t('common.unavailable'))}
                                  {match.venue ? ` · ${match.venue}` : ''}
                                </span>
                              </span>
                            )}
                            <Badge tone={STATE_TONES[match.calendarState] ?? 'subtle'}>
                              {stateLabel(t, match.calendarState)}
                            </Badge>
                            {manual ? (
                              <span className="club-source">
                                {t('calendarPage.manualOverdue')}
                                {match.overdueMarkedBy
                                  ? ` ${t('calendarPage.markedBy', {
                                      user: match.overdueMarkedBy,
                                      date: formatDateTime(match.overdueMarkedAt, ''),
                                    })}`
                                  : ''}
                              </span>
                            ) : null}
                            {markable ? (
                              <Button
                                variant="secondary"
                                onClick={() => toggleMark(match)}
                                disabled={busyId === match.id}
                                aria-label={`${manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')} ${teamLabel}`}
                              >
                                {busyId === match.id
                                  ? t('calendarPage.markBusy')
                                  : manual ? t('calendarPage.clearAction') : t('calendarPage.markAction')}
                              </Button>
                            ) : null}
                          </li>
                        )
                      })}
                    </ul>
                  </li>
                )
              })}
            </ol>
          </Card>
        )
      })}
    </section>
  )
}

export default SeasonCalendarPage