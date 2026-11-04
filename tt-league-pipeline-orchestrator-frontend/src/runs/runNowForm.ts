import type { PipelineSource, ScopeFilter, ScopeType, TriggerRunRequest } from '../api/types'

export interface FilterDraft {
  readonly category: string
  readonly group: string
  readonly phase: string
  readonly territory: string
  readonly gender: string
  /** Comma separated positive integers, e.g. `3, 4`. */
  readonly matchDays: string
}

export interface RunNowForm {
  readonly source: PipelineSource | 'ALL' | ''
  readonly season: string
  readonly scopeType: ScopeType | ''
  readonly filters: readonly FilterDraft[]
  readonly force: boolean
}

export interface RunNowErrors {
  source?: string
  season?: string
  scopeType?: string
  /** One message per filter row that is invalid, plus `filters` for list-level problems. */
  filters?: string
  filterRows?: Readonly<Record<number, string>>
}

export const FILTER_FIELDS = ['category', 'group', 'phase', 'territory', 'gender'] as const

export function emptyFilter(): FilterDraft {
  return { category: '', group: '', phase: '', territory: '', gender: '', matchDays: '' }
}

export function emptyRunNowForm(): RunNowForm {
  return { source: '', season: '', scopeType: '', filters: [emptyFilter()], force: false }
}

const SEASON_PATTERN = /^(\d{4})-(\d{4})$/

/** Mirrors `PipelineRun.requireValidSeason`. */
export function seasonError(season: string): string | undefined {
  const match = SEASON_PATTERN.exec(season.trim())
  if (match === null) {
    return 'Use the form 2025-2026'
  }
  if (Number(match[2]) !== Number(match[1]) + 1) {
    return 'The years must be consecutive, for example 2025-2026'
  }
  return undefined
}

export function parseMatchDays(text: string): readonly number[] | null | 'invalid' {
  const trimmed = text.trim()
  if (trimmed === '') {
    return null
  }
  const numbers: number[] = []
  for (const part of trimmed.split(',')) {
    const value = part.trim()
    if (!/^[1-9]\d*$/.test(value)) {
      return 'invalid'
    }
    numbers.push(Number(value))
  }
  return numbers
}

function isBlankFilter(filter: FilterDraft): boolean {
  return [...FILTER_FIELDS.map((field) => filter[field]), filter.matchDays].every((value) => value.trim() === '')
}

/** Mirrors `TriggerRules`; for fast feedback only, the server stays the authority. */
export function validateRunNow(form: RunNowForm): RunNowErrors {
  const errors: RunNowErrors = {}
  if (form.source === '') {
    errors.source = 'Choose a source'
  }
  if (form.season.trim() === '') {
    errors.season = 'Enter the season'
  } else {
    errors.season = seasonError(form.season)
  }
  if (form.scopeType === '') {
    errors.scopeType = 'Choose a scope'
  }
  if (form.scopeType === 'GROUP') {
    if (form.source === 'ALL') {
      errors.scopeType = 'Group scope needs exactly one source'
    }
    const rows: Record<number, string> = {}
    if (form.filters.length === 0) {
      errors.filters = 'Add at least one filter'
    }
    form.filters.forEach((filter, index) => {
      if (isBlankFilter(filter)) {
        rows[index] = 'Fill at least one field'
      } else if (parseMatchDays(filter.matchDays) === 'invalid') {
        rows[index] = 'Match days must be positive whole numbers separated by commas'
      }
    })
    if (Object.keys(rows).length > 0) {
      errors.filterRows = rows
    }
  }
  if (errors.season === undefined) {
    delete errors.season
  }
  return errors
}

export function hasErrors(errors: RunNowErrors): boolean {
  return Object.keys(errors).length > 0
}

function textOrNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function toScopeFilter(filter: FilterDraft): ScopeFilter {
  const matchDays = parseMatchDays(filter.matchDays)
  return {
    category: textOrNull(filter.category),
    group: textOrNull(filter.group),
    phase: textOrNull(filter.phase),
    territory: textOrNull(filter.territory),
    gender: textOrNull(filter.gender),
    matchDays: matchDays === 'invalid' ? null : matchDays,
  }
}

/** Builds the request from a valid form: trimmed values, blank fields sent as absent, filters only for `GROUP`. */
export function toTriggerRequest(form: RunNowForm): TriggerRunRequest {
  if (form.source === '' || form.scopeType === '') {
    throw new Error('toTriggerRequest needs a valid form')
  }
  const base = { source: form.source, season: form.season.trim(), scopeType: form.scopeType, force: form.force }
  if (form.scopeType !== 'GROUP') {
    return base
  }
  const filters = form.filters.filter((filter) => !isBlankFilter(filter)).map(toScopeFilter)
  return { ...base, filters: filters.map(dropNulls) }
}

function dropNulls(filter: ScopeFilter): Partial<ScopeFilter> {
  return Object.fromEntries(Object.entries(filter).filter(([, value]) => value !== null)) as Partial<ScopeFilter>
}
