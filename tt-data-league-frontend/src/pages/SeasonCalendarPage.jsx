import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { format, parseISO } from 'date-fns'
import { clearMatchOverdueMark, getMatchOptions, markMatchOverdue } from '../api/matches.js'
import { useCalendarRange, useSeasonCalendar } from '../hooks/useMatches.js'
import { useAuth } from '../context/useAuth.js'
import { useAppState } from '../context/useAppState.js'
import Badge from '../components/ui/Badge.jsx'
import ProgressBar from '../components/ui/ProgressBar.jsx'
import Card from '../components/ui/Card.jsx'
import CalendarDetailPanel from '../components/calendar/CalendarDetailPanel.jsx'
import CalendarFilters from '../components/calendar/CalendarFilters.jsx'
import CalendarToolbar from '../components/calendar/CalendarToolbar.jsx'
import MatchCalendar from '../components/calendar/MatchCalendar.jsx'
import { matchDay, normalizeSort, toEvents } from '../components/calendar/calendarEvents.js'
import {
  dateFnsLocale, normalizeDate, normalizeView, rangeFor, shift, titleFor,
} from '../components/calendar/calendarRange.js'

const EMPTY_OPTIONS = { seasons: [], competitions: [] }
const NO_MATCHES = []

function includesTeam(match, team) {
  return !team || match.homeTeamId === team || match.awayTeamId === team
}

function roundDates(rounds) {
  const dates = rounds.flatMap((round) => [round.firstDate, round.lastDate]).filter(Boolean).sort()
  return dates.length > 0 ? { firstDate: dates[0], lastDate: dates[dates.length - 1] } : null
}

function SeasonCalendarPage() {
  const { t, i18n } = useTranslation()
  const { token, clearSession, hasPermission } = useAuth()
  const { viewport } = useAppState()
  const compact = viewport !== 'desktop'
  const [params, setParams] = useSearchParams()

  const view = normalizeView(params.get('view'))
  const date = normalizeDate(params.get('date'))
  const sort = normalizeSort(params.get('sort'))
  const selected = params.get('selected') ?? ''
  const roundParam = params.get('round') ?? ''
  const filters = useMemo(() => ({
    source: params.get('source') ?? '',
    season: params.get('season') ?? '',
    competition: params.get('competition') ?? '',
    group: params.get('group') ?? '',
    team: params.get('team') ?? '',
    sort,
  }), [params, sort])
  const jornada = view === 'jornada'

  const [options, setOptions] = useState(EMPTY_OPTIONS)
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [optionsError, setOptionsError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [focusMatchId, setFocusMatchId] = useState(null)

  const range = useMemo(() => rangeFor(view, date), [view, date])
  const rangeState = useCalendarRange({
    source: jornada ? '' : filters.source,
    season: filters.season,
    from: range?.from ?? '',
    to: range?.to ?? '',
    competition: filters.competition,
    group: filters.group,
    team: filters.team,
  })
  const seasonState = useSeasonCalendar({
    source: jornada ? filters.source : '',
    season: filters.season,
    competition: filters.competition,
    group: '',
    round: '',
  })
  const { data, loading, error, retry } = jornada ? seasonState : rangeState

  useEffect(() => {
    if (!filters.source) return undefined
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

  const update = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, String(value))
      else next.delete(key)
    })
    setActionError(null)
    setParams(next)
  }

  const onFilterChange = (key, value) => {
    const changes = { [key]: value, selected: '' }
    if (key === 'source') {
      Object.assign(changes, { season: '', competition: '', group: '', team: '', round: '' })
      setOptions(EMPTY_OPTIONS)
    } else if (key === 'season') {
      Object.assign(changes, { competition: '', group: '', team: '', round: '' })
    } else if (key === 'competition') {
      Object.assign(changes, { group: '', team: '', round: '' })
    } else if (key === 'group') {
      changes.round = ''
    }
    update(changes)
  }

  // Jornada view: the selected competition's groups and rounds come from the season calendar.
  const groups = useMemo(() => (jornada ? data?.groups ?? [] : []), [jornada, data])
  const groupNumbers = useMemo(() => [...new Set(
    groups.map((group) => group.groupNumber).filter((value) => value != null),
  )].sort((a, b) => a - b), [groups])
  const effectiveGroup = useMemo(() => {
    if (!jornada) return ''
    if (filters.group && groupNumbers.includes(Number(filters.group))) return filters.group
    return groupNumbers.length > 0 ? String(groupNumbers[0]) : ''
  }, [filters.group, groupNumbers, jornada])
  const activeGroups = useMemo(
    () => groups.filter((group) => (group.groupNumber == null
      ? !effectiveGroup
      : String(group.groupNumber) === effectiveGroup)),
    [groups, effectiveGroup],
  )
  const roundNumbers = useMemo(() => [...new Set(
    activeGroups.flatMap((group) => group.rounds.map((round) => round.round)),
  )].sort((a, b) => a - b), [activeGroups])
  const currentRound = activeGroups.find((group) => group.currentRound != null)?.currentRound ?? null
  const round = useMemo(() => {
    const requested = Number(roundParam)
    if (roundNumbers.includes(requested)) return requested
    if (currentRound != null && roundNumbers.includes(currentRound)) return currentRound
    return roundNumbers[0] ?? null
  }, [roundParam, roundNumbers, currentRound])
  const jornadaRounds = useMemo(
    () => activeGroups.flatMap((group) => group.rounds).filter((entry) => entry.round === round),
    [activeGroups, round],
  )
  const jornadaMatches = useMemo(
    () => jornadaRounds.flatMap((entry) => entry.matches).filter((match) => includesTeam(match, filters.team)),
    [jornadaRounds, filters.team],
  )

  const rangeMatches = jornada ? NO_MATCHES : data?.matches ?? NO_MATCHES
  const calendarMatches = jornada ? jornadaMatches : rangeMatches
  const events = useMemo(() => toEvents(calendarMatches), [calendarMatches])
  const detailMatches = useMemo(() => {
    if (jornada || !selected) return calendarMatches
    return calendarMatches.filter((match) => matchDay(match) === selected)
  }, [calendarMatches, jornada, selected])

  const filterOptions = useMemo(() => {
    if (jornada) {
      const seen = new Map()
      groups.flatMap((group) => group.rounds.flatMap((entry) => entry.matches)).forEach((match) => {
        ;[[match.homeTeamId, match.homeTeamName], [match.awayTeamId, match.awayTeamName]].forEach(([id, name]) => {
          if (id && !seen.has(id)) seen.set(id, { teamId: id, name, competition: filters.competition })
        })
      })
      return {
        ...options,
        groups: groupNumbers,
        teams: [...seen.values()].sort((a, b) => String(a.name).localeCompare(String(b.name))),
      }
    }
    return {
      ...options,
      groups: data?.facets?.groups ?? [],
      teams: data?.facets?.teams ?? [],
    }
  }, [data, filters.competition, groupNumbers, groups, jornada, options])

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

  const changeView = (next) => {
    update({ view: next, selected: '' })
    setFocusMatchId(null)
  }
  const move = (direction) => update({ date: shift(view, date, direction), selected: '' })
  const selectDate = (value) => {
    setFocusMatchId(null)
    update({ selected: value })
  }
  const selectMatch = (match) => {
    setFocusMatchId(match.id)
    update({ selected: matchDay(match) ?? '' })
  }

  const requestedReady = jornada
    ? Boolean(filters.source && filters.season && filters.competition)
    : Boolean(filters.source && filters.season)
  const language = i18n.language
  const title = titleFor(view, date, language)
  const detailHeading = selected
    ? format(parseISO(selected), 'PPPP', { locale: dateFnsLocale(language) })
    : jornada && round != null
      ? t('calendarPage.roundLabel', { round })
      : title
  const hasResults = detailMatches.length > 0 || calendarMatches.length > 0
  const visibleRange = jornada ? roundDates(jornadaRounds) : null
  const returnSearch = params

  return (
    <section className="page-block">
      <h1 className="page-title">{t('calendarPage.title')}</h1>
      <p className="page-description">{t('calendarPage.description')}</p>

      <CalendarFilters
        filters={{ ...filters, group: jornada ? effectiveGroup : filters.group }}
        options={filterOptions}
        loadingOptions={loadingOptions}
        optionalScope={!jornada}
        onChange={onFilterChange}
      />

      <CalendarToolbar
        view={view}
        title={title}
        onViewChange={changeView}
        onPrevious={() => move(-1)}
        onNext={() => move(1)}
        onToday={() => update({ date: '', selected: '' })}
        rounds={roundNumbers}
        round={round}
        onRoundChange={(value) => update({ round: value, selected: '' })}
      />

      {actionError ? <p role="alert">{actionError.status === 409 ? t('calendarPage.markConflict') : t('calendarPage.markError')}</p> : null}
      {optionsError && !data ? <p role="alert">{t('calendarPage.error')}</p> : null}
      {error ? (
        <p role="alert">{error.status === 401 ? t('calendarPage.unauthorized') : t('calendarPage.error')}</p>
      ) : null}
      {jornada && filters.source && filters.season && !filters.competition
        ? <p role="status">{t('calendarPage.selectCompetition')}</p> : null}
      {!requestedReady && !jornada ? <p role="status">{t('calendarPage.selectSeason')}</p> : null}
      {requestedReady && loading ? <p role="status">{t('calendarPage.loading')}</p> : null}
      {requestedReady && !loading && !error && data && !hasResults ? (
        <p role="status">{t('calendarPage.empty')}</p>
      ) : null}

      {data?.overdueGraceDays != null ? (
        <p className="page-description">{t('calendarPage.legend', { days: data.overdueGraceDays })}</p>
      ) : null}

      {jornada ? activeGroups.map((group) => {
        const total = group.scheduledMatches + group.playedMatches
        const progress = total > 0 ? (group.playedMatches / total) * 100 : null
        return (
          <Card key={`${group.groupNumber ?? ''}-${group.phase ?? ''}`} className="calendar-group-card">
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
              {group.currentRound === round ? <Badge tone="subtle">{t('calendarPage.current')}</Badge> : null}
            </div>
            <ProgressBar
              value={progress}
              label={t('calendarPage.progress')}
              valueText={`${group.playedMatches} / ${total}`}
            />
          </Card>
        )
      }) : null}

      <div className="calendar-layout">
        <div className="calendar-layout-grid">
          {(!jornada || visibleRange) ? (
            <MatchCalendar
              view={view}
              date={date}
              events={events}
              visibleRange={visibleRange}
              compact={compact}
              onDateSelect={selectDate}
              onMatchSelect={selectMatch}
            />
          ) : null}
        </div>
        <CalendarDetailPanel
          heading={detailHeading}
          matches={detailMatches}
          sort={sort}
          returnSearch={returnSearch}
          canWrite={hasPermission('matches:write')}
          busyId={busyId}
          focusMatchId={focusMatchId}
          onToggleMark={toggleMark}
        />
      </div>
    </section>
  )
}

export default SeasonCalendarPage
