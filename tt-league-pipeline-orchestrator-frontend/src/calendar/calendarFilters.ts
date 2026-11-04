import type { MatchDayListQuery } from '../api/matchDays'
import type { MatchDayState, MatchDaySummary, PipelineSource } from '../api/types'
import { SOURCES } from '../runs/runFilters'
import { isValidIsoDate, visibleRange } from './calendarDates'
import type { CalendarView } from './calendarDates'

export const MATCH_DAY_PAGE_SIZE = 200
const STATES: readonly MatchDayState[] = ['UPCOMING', 'OPEN', 'CLOSED']
const SEASON_PATTERN = /^\d{4}-\d{4}$/
const MAX_TEXT = 255

/** The calendar state kept in the URL. `view` and `date` are navigation; the rest are data filters. */
export interface CalendarFilters {
  readonly view: CalendarView
  readonly date: string
  readonly source: PipelineSource | null
  readonly season: string | null
  readonly competition: string | null
  readonly phase: string | null
  readonly state: MatchDayState | null
  /** Invalid values that were dropped; shown to the user, never silently replaced. */
  readonly errors: readonly string[]
}

export type CalendarFilterValues = Omit<CalendarFilters, 'errors'>

/** Missing `view` and `date` default to the month and today (navigation only); no data filter has a default. */
export function parseCalendarFilters(params: URLSearchParams, today: string): CalendarFilters {
  const errors: string[] = []

  let view: CalendarView = 'month'
  const rawView = params.get('view')
  if (rawView === 'month' || rawView === 'week') {
    view = rawView
  } else if (rawView !== null) {
    errors.push(`Unknown view "${rawView}" was ignored; showing the month.`)
  }

  let date = today
  const rawDate = params.get('date')
  if (rawDate !== null) {
    if (isValidIsoDate(rawDate)) {
      date = rawDate
    } else {
      errors.push(`Invalid date "${rawDate}" was ignored; showing today.`)
    }
  }

  let source: PipelineSource | null = null
  const rawSource = params.get('source')
  if (rawSource !== null && rawSource !== '') {
    if ((SOURCES as readonly string[]).includes(rawSource)) {
      source = rawSource as PipelineSource
    } else {
      errors.push(`Unknown source "${rawSource}" was ignored.`)
    }
  }

  let season: string | null = null
  const rawSeason = params.get('season')
  if (rawSeason !== null && rawSeason !== '') {
    if (SEASON_PATTERN.test(rawSeason)) {
      season = rawSeason
    } else {
      errors.push(`Invalid season "${rawSeason}" was ignored.`)
    }
  }

  const text = (name: string): string | null => {
    const value = params.get(name)
    if (value === null || value === '') {
      return null
    }
    if (value.trim() === '' || value.length > MAX_TEXT) {
      errors.push(`Invalid ${name} was ignored.`)
      return null
    }
    return value
  }
  const competition = text('competition')
  const phase = text('phase')

  let state: MatchDayState | null = null
  const rawState = params.get('state')
  if (rawState !== null && rawState !== '') {
    if ((STATES as readonly string[]).includes(rawState)) {
      state = rawState as MatchDayState
    } else {
      errors.push(`Unknown state "${rawState}" was ignored.`)
    }
  }

  return { view, date, source, season, competition, phase, state, errors }
}

/** Writes every value; `view` and `date` are always present so a shared link opens the same period. */
export function serializeCalendarFilters(filters: CalendarFilterValues): URLSearchParams {
  const params = new URLSearchParams()
  params.set('view', filters.view)
  params.set('date', filters.date)
  if (filters.source !== null) {
    params.set('source', filters.source)
  }
  if (filters.season !== null) {
    params.set('season', filters.season)
  }
  if (filters.competition !== null) {
    params.set('competition', filters.competition)
  }
  if (filters.phase !== null) {
    params.set('phase', filters.phase)
  }
  if (filters.state !== null) {
    params.set('state', filters.state)
  }
  return params
}

export function hasDataFilters(filters: CalendarFilterValues): boolean {
  return (
    filters.source !== null ||
    filters.season !== null ||
    filters.competition !== null ||
    filters.phase !== null ||
    filters.state !== null
  )
}

function dataQuery(filters: CalendarFilterValues): MatchDayListQuery {
  return {
    source: filters.source ?? undefined,
    season: filters.season ?? undefined,
    competition: filters.competition ?? undefined,
    phase: filters.phase ?? undefined,
    state: filters.state ?? undefined,
    size: MATCH_DAY_PAGE_SIZE,
  }
}

/** The dated match days of the visible period (one page of it). */
export function toRangeQuery(filters: CalendarFilterValues, page: number): MatchDayListQuery {
  const { from, to } = visibleRange(filters.view, filters.date)
  return { ...dataQuery(filters), from, to, page }
}

/** The undated match days under the same data filters. */
export function toUndatedQuery(filters: CalendarFilterValues): MatchDayListQuery {
  return { ...dataQuery(filters), undated: true, page: 0 }
}

/**
 * Entries per day, in API order. A match day sits on its first date; one that started before the first visible day
 * (its window overlaps the period) sits on the first visible day, so it is not hidden.
 */
export function placeEntries(
  summaries: readonly MatchDaySummary[],
  days: readonly string[],
): ReadonlyMap<string, readonly MatchDaySummary[]> {
  const placed = new Map<string, MatchDaySummary[]>(days.map((day) => [day, []]))
  if (days.length === 0) {
    return placed
  }
  const first = days[0]
  const last = days[days.length - 1]
  for (const summary of summaries) {
    if (summary.firstDate === null) {
      continue
    }
    const day = summary.firstDate < first ? first : summary.firstDate
    if (day > last) {
      continue
    }
    placed.get(day)?.push(summary)
  }
  return placed
}
