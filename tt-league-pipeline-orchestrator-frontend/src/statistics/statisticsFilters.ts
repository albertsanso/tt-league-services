import type { PipelineSource } from '../api/types'
import { isValidDate, SOURCES } from '../runs/runFilters'

/** Days in the default range (today included). */
export const DEFAULT_RANGE_DAYS = 30
/** The server rejects longer ranges. */
export const MAX_RANGE_DAYS = 366

export interface StatisticsFilters {
  readonly sources: readonly PipelineSource[]
  /** The season of the time-to-report and reporting-progress panels; null until one is chosen. */
  readonly season: string | null
  /** Local `YYYY-MM-DD`, inclusive. */
  readonly fromDate: string
  readonly toDate: string
  readonly errors: readonly string[]
}

const SEASON_PATTERN = /^(\d{4})-(\d{4})$/

export function isValidSeason(value: string): boolean {
  const match = SEASON_PATTERN.exec(value)
  return match !== null && Number(match[2]) === Number(match[1]) + 1
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** Local `YYYY-MM-DD` of a date shifted by whole days. */
export function formatLocalDate(date: Date, addDays = 0): string {
  const shifted = new Date(date.getFullYear(), date.getMonth(), date.getDate() + addDays)
  return `${shifted.getFullYear()}-${pad(shifted.getMonth() + 1)}-${pad(shifted.getDate())}`
}

export function defaultRange(today: Date): { readonly fromDate: string; readonly toDate: string } {
  return { fromDate: formatLocalDate(today, -(DEFAULT_RANGE_DAYS - 1)), toDate: formatLocalDate(today) }
}

function toDay(value: string): number {
  const [year, month, day] = value.split('-').map(Number)
  return Date.UTC(year, month - 1, day) / 86_400_000
}

/** Inclusive number of days between two valid dates. */
export function rangeDays(fromDate: string, toDate: string): number {
  return toDay(toDate) - toDay(fromDate) + 1
}

/** A message when the range cannot be requested, otherwise null. */
export function rangeError(filters: Pick<StatisticsFilters, 'fromDate' | 'toDate'>): string | null {
  const days = rangeDays(filters.fromDate, filters.toDate)
  if (days < 1) {
    return 'The "from" date is after the "to" date.'
  }
  if (days > MAX_RANGE_DAYS) {
    return `The range is longer than ${MAX_RANGE_DAYS} days.`
  }
  return null
}

/** Reads the filters from the URL; a missing or invalid range falls back to the last 30 days. */
export function parseStatisticsFilters(params: URLSearchParams, today: Date = new Date()): StatisticsFilters {
  const errors: string[] = []
  const sources: PipelineSource[] = []
  for (const value of params.getAll('source')) {
    if ((SOURCES as readonly string[]).includes(value)) {
      if (!sources.includes(value as PipelineSource)) {
        sources.push(value as PipelineSource)
      }
    } else {
      errors.push(`Unknown source "${value}" was ignored.`)
    }
  }
  let season: string | null = null
  const rawSeason = params.get('season')
  if (rawSeason !== null && rawSeason !== '') {
    if (isValidSeason(rawSeason)) {
      season = rawSeason
    } else {
      errors.push(`Invalid season "${rawSeason}" was ignored.`)
    }
  }
  const defaults = defaultRange(today)
  const date = (name: 'from' | 'to', fallback: string): string => {
    const value = params.get(name)
    if (value === null || value === '') {
      return fallback
    }
    if (!isValidDate(value)) {
      errors.push(`Invalid "${name}" date "${value}" was ignored.`)
      return fallback
    }
    return value
  }
  const fromDate = date('from', defaults.fromDate)
  const toDate = date('to', defaults.toDate)
  return { sources, season, fromDate, toDate, errors }
}

export function serializeStatisticsFilters(filters: Omit<StatisticsFilters, 'errors'>): URLSearchParams {
  const params = new URLSearchParams()
  filters.sources.forEach((source) => params.append('source', source))
  if (filters.season !== null) {
    params.set('season', filters.season)
  }
  params.set('from', filters.fromDate)
  params.set('to', filters.toDate)
  return params
}
