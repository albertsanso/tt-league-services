import type { RunListQuery } from '../api/runs'
import type { PipelineSource, RunStatus } from '../api/types'
import { ACTIVE_STATUSES, TERMINAL_STATUSES } from './runStatus'

export const PAGE_SIZE = 20
export const SOURCES: readonly PipelineSource[] = ['RFETM', 'BCNESA', 'FCTT']
const STATUSES: readonly RunStatus[] = [...ACTIVE_STATUSES, ...TERMINAL_STATUSES]

export interface RunFilters {
  readonly sources: readonly PipelineSource[]
  readonly statuses: readonly RunStatus[]
  /** Local `YYYY-MM-DD`, or null. */
  readonly fromDate: string | null
  readonly toDate: string | null
  /** 1-based, as in the URL. */
  readonly page: number
  readonly errors: readonly string[]
}

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

export function isValidDate(value: string): boolean {
  const match = DATE_PATTERN.exec(value)
  if (match === null) {
    return false
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
}

export function parseRunFilters(params: URLSearchParams): RunFilters {
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
  const statuses: RunStatus[] = []
  for (const value of params.getAll('status')) {
    if ((STATUSES as readonly string[]).includes(value)) {
      if (!statuses.includes(value as RunStatus)) {
        statuses.push(value as RunStatus)
      }
    } else {
      errors.push(`Unknown status "${value}" was ignored.`)
    }
  }
  const date = (name: string): string | null => {
    const value = params.get(name)
    if (value === null || value === '') {
      return null
    }
    if (!isValidDate(value)) {
      errors.push(`Invalid "${name}" date "${value}" was ignored.`)
      return null
    }
    return value
  }
  const fromDate = date('from')
  const toDate = date('to')
  if (fromDate !== null && toDate !== null && fromDate > toDate) {
    errors.push('The "from" date is after the "to" date; no runs were requested.')
  }
  let page = 1
  const rawPage = params.get('page')
  if (rawPage !== null) {
    if (/^[1-9]\d*$/.test(rawPage)) {
      page = Number(rawPage)
    } else {
      errors.push(`Invalid page "${rawPage}" was ignored.`)
    }
  }
  return { sources, statuses, fromDate, toDate, page, errors }
}

export function hasRangeError(filters: Pick<RunFilters, 'fromDate' | 'toDate'>): boolean {
  return filters.fromDate !== null && filters.toDate !== null && filters.fromDate > filters.toDate
}

export function hasActiveFilters(filters: RunFilters): boolean {
  return filters.sources.length > 0 || filters.statuses.length > 0 || filters.fromDate !== null || filters.toDate !== null
}

export function serializeRunFilters(filters: Omit<RunFilters, 'errors'>): URLSearchParams {
  const params = new URLSearchParams()
  filters.sources.forEach((source) => params.append('source', source))
  filters.statuses.forEach((status) => params.append('status', status))
  if (filters.fromDate !== null) {
    params.set('from', filters.fromDate)
  }
  if (filters.toDate !== null) {
    params.set('to', filters.toDate)
  }
  if (filters.page > 1) {
    params.set('page', String(filters.page))
  }
  return params
}

function localStart(date: string, addDays = 0): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day + addDays).toISOString()
}

/** `from` is the start of the from-day; `to` is the start of the day after the to-day (exclusive bound). */
export function toRunListQuery(filters: RunFilters): RunListQuery {
  return {
    source: filters.sources.length > 0 ? filters.sources : undefined,
    status: filters.statuses.length > 0 ? filters.statuses : undefined,
    from: filters.fromDate !== null ? localStart(filters.fromDate) : undefined,
    to: filters.toDate !== null ? localStart(filters.toDate, 1) : undefined,
    page: filters.page - 1,
    size: PAGE_SIZE,
  }
}
