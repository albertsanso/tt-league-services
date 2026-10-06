import type { RunSummary, RunUnit, ScopeFilter, StepEvent, StepStatusSummary } from '../api/types'
import { isTerminalUnitStatus, STEP_ORDER } from './runStatus'

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

/** One text for a filter: its identity parts, then its match days. */
export function filterText(filter: ScopeFilter): string {
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

/**
 * Replaces the kind's entry with the event. Units run one after the other, so the newest event of a kind is the latest
 * attempt of the run, whichever unit it belongs to; keeps `STEP_ORDER`.
 */
export function mergeStepSummary(
  steps: readonly StepStatusSummary[] | undefined,
  event: Pick<StepEvent, 'kind' | 'status' | 'attempt'>,
): readonly StepStatusSummary[] {
  const current = steps ?? []
  const next = { kind: event.kind, status: event.status, attempt: event.attempt }
  return [...current.filter((step) => step.kind !== event.kind), next].sort(
    (a, b) => stepIndex(a.kind) - stepIndex(b.kind),
  )
}

/** Upserts a step by `(unitId, kind, attempt)`, ordered by start time, then kind and attempt. */
export function upsertStep<T extends StepEvent>(steps: readonly T[], event: T): readonly T[] {
  const rest = steps.filter(
    (step) => !(step.unitId === event.unitId && step.kind === event.kind && step.attempt === event.attempt),
  )
  return [...rest, event].sort(
    (a, b) =>
      Date.parse(a.startedAt ?? '') - Date.parse(b.startedAt ?? '') ||
      stepIndex(a.kind) - stepIndex(b.kind) ||
      a.attempt - b.attempt,
  )
}

/** True when `incoming` reports an older progress than `current` for the same status: a late event to ignore. */
export function isStaleUnitEvent(current: RunUnit, incoming: RunUnit): boolean {
  const known = current.progress?.updatedAt
  const reported = incoming.progress?.updatedAt
  return (
    known !== undefined &&
    reported !== undefined &&
    current.status === incoming.status &&
    Date.parse(reported) < Date.parse(known)
  )
}

/**
 * Applies a `unit` event to the unit with the same id, keeping the fields the event does not carry (the import
 * counters, and on a detail unit its steps and artifacts); a late progress event is ignored and an unknown unit leaves
 * the list unchanged.
 */
export function patchUnit<T extends RunUnit>(units: readonly T[], event: RunUnit): readonly T[] {
  const index = units.findIndex((unit) => unit.id === event.id)
  if (index < 0 || isStaleUnitEvent(units[index], event)) {
    return units
  }
  const merged = { ...units[index], ...event, counters: event.counters ?? units[index].counters }
  return units.map((unit, position) => (position === index ? merged : unit))
}

/** Like {@link patchUnit}, but a unit that is not in the list yet is added in ordinal order. */
export function upsertUnit(units: readonly RunUnit[] | undefined, event: RunUnit): readonly RunUnit[] {
  const current = units ?? []
  if (current.some((unit) => unit.id === event.id)) {
    return patchUnit(current, event)
  }
  return [...current, event].sort((a, b) => a.ordinal - b.ordinal)
}

/** "2/3 units": the finished units over all of them; empty when a run has no units yet. */
export function unitsSummary(units: readonly RunUnit[] | undefined): string {
  if (units === undefined || units.length === 0) {
    return ''
  }
  const finished = units.filter((unit) => isTerminalUnitStatus(unit.status)).length
  return `${finished}/${units.length} unit${units.length === 1 ? '' : 's'}`
}

/** Short text of a unit's progress: the stage, `n/m` (or `n` when the total is unknown) and the last item. */
export function progressText(progress: RunUnit['progress']): string {
  if (progress === null) {
    return ''
  }
  const parts: string[] = []
  if (progress.stage !== null) {
    parts.push(progress.stage)
  }
  if (progress.itemsTotal !== null) {
    parts.push(`${progress.itemsProcessed}/${progress.itemsTotal}`)
  } else if (progress.itemsProcessed > 0) {
    parts.push(String(progress.itemsProcessed))
  }
  if (progress.currentItem !== null) {
    parts.push(progress.currentItem)
  }
  return parts.join(' · ')
}
