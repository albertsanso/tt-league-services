import type { RunSummary, ScopeFilter, StepEvent, StepStatusSummary } from '../api/types'
import { STEP_ORDER } from './runStatus'

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms < 0) {
    return '—'
  }
  const totalSeconds = Math.floor(ms / 1000)
  if (totalSeconds < 60) {
    return `${totalSeconds} s`
  }
  const pad = (value: number) => String(value).padStart(2, '0')
  const totalMinutes = Math.floor(totalSeconds / 60)
  if (totalMinutes < 60) {
    return `${totalMinutes} min ${pad(totalSeconds % 60)} s`
  }
  return `${Math.floor(totalMinutes / 60)} h ${pad(totalMinutes % 60)} min`
}

/** Milliseconds from `startedAt` to `finishedAt` (or `now`); null when the run has not started. */
export function elapsedMs(startedAt: string | null, finishedAt: string | null, now: number): number | null {
  if (startedAt === null) {
    return null
  }
  const end = finishedAt === null ? now : Date.parse(finishedAt)
  return Math.max(0, end - Date.parse(startedAt))
}

export function formatInstant(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KiB', 'MiB', 'GiB', 'TiB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unit]}`
}

function filterText(filter: ScopeFilter): string {
  const parts = [filter.category, filter.group, filter.phase, filter.territory, filter.gender].filter(
    (value): value is string => value !== null && value !== undefined && value !== '',
  )
  if (filter.matchDays && filter.matchDays.length > 0) {
    parts.push(`md ${filter.matchDays.join(', ')}`)
  }
  return parts.join(' · ')
}

type ScopedRun = Pick<RunSummary, 'fullSeason' | 'filters'>

/** Short scope text for a table cell: at most two filters, then `+N more`. */
export function scopeLabel(run: ScopedRun): string {
  if (run.fullSeason) {
    return 'Full season'
  }
  const texts = run.filters.map(filterText).filter((text) => text !== '')
  if (texts.length === 0) {
    return 'Open match days'
  }
  const shown = texts.slice(0, 2).join(' | ')
  return texts.length > 2 ? `${shown} +${texts.length - 2} more` : shown
}

/** Every filter, one text each, for a tooltip. */
export function scopeDetails(run: ScopedRun): readonly string[] {
  if (run.fullSeason) {
    return ['Full season']
  }
  const texts = run.filters.map(filterText).filter((text) => text !== '')
  return texts.length === 0 ? ['Open match days'] : texts
}

/** Event fields over the row, keeping the row's `steps`. */
export function mergeRunEvent<T extends RunSummary>(row: T, event: Omit<RunSummary, 'steps'>): T {
  return { ...row, ...event, steps: row.steps }
}

function stepIndex(kind: StepStatusSummary['kind']): number {
  return STEP_ORDER.indexOf(kind)
}

/** Replaces the kind's entry when the event's attempt is the same or newer; keeps `STEP_ORDER`. */
export function mergeStepSummary(
  steps: readonly StepStatusSummary[] | undefined,
  event: Pick<StepEvent, 'kind' | 'status' | 'attempt'>,
): readonly StepStatusSummary[] {
  const current = steps ?? []
  const existing = current.find((step) => step.kind === event.kind)
  if (existing && existing.attempt > event.attempt) {
    return current
  }
  const next = { kind: event.kind, status: event.status, attempt: event.attempt }
  return [...current.filter((step) => step.kind !== event.kind), next].sort(
    (a, b) => stepIndex(a.kind) - stepIndex(b.kind),
  )
}

/** Upserts a step by `(kind, attempt)`, ordered by kind then attempt. */
export function upsertStep(steps: readonly StepEvent[], event: StepEvent): readonly StepEvent[] {
  const rest = steps.filter((step) => !(step.kind === event.kind && step.attempt === event.attempt))
  return [...rest, event].sort((a, b) => stepIndex(a.kind) - stepIndex(b.kind) || a.attempt - b.attempt)
}
