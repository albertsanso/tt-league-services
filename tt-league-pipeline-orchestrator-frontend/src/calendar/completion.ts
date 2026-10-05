import type { MatchDayCompletion, MatchDayState, MatchDaySummary, TrackedMatchStatus } from '../api/types'

/** The server decides the completion category (`TrackerRules.completion`); this only maps it to colours and text. */
export type CompletionColor = 'success' | 'info' | 'error' | 'default'

const COLORS: Readonly<Record<MatchDayCompletion, CompletionColor>> = {
  COMPLETE: 'success',
  IN_PROGRESS: 'info',
  HAS_OVERDUE: 'error',
  FUTURE: 'default',
}

export const COMPLETION_LABELS: Readonly<Record<MatchDayCompletion, string>> = {
  COMPLETE: 'All reported',
  IN_PROGRESS: 'In progress',
  HAS_OVERDUE: 'Has overdue',
  FUTURE: 'Future',
}

export const COMPLETION_ORDER: readonly MatchDayCompletion[] = ['COMPLETE', 'IN_PROGRESS', 'HAS_OVERDUE', 'FUTURE']

export const STATE_LABELS: Readonly<Record<MatchDayState, string>> = {
  UPCOMING: 'Upcoming',
  OPEN: 'Open',
  CLOSED: 'Closed',
}

export const MATCH_STATUS_LABELS: Readonly<Record<TrackedMatchStatus, string>> = {
  SCHEDULED: 'Scheduled',
  AWAITING_RESULT: 'Awaiting result',
  REPORTED: 'Reported',
  POSTPONED: 'Postponed',
  OVERDUE: 'Overdue',
}

export function completionColor(completion: MatchDayCompletion): CompletionColor {
  return COLORS[completion]
}

type Labelled = Pick<MatchDaySummary, 'competition' | 'groupNumber' | 'phase' | 'round'>

/** `TERCERA-masculino · G2 · 1a Fase · J3`; group and phase are left out when the match day has none. */
export function entryLabel(summary: Labelled): string {
  const parts = [summary.competition]
  if (summary.groupNumber !== null) {
    parts.push(`G${summary.groupNumber}`)
  }
  if (summary.phase !== null && summary.phase !== '') {
    parts.push(summary.phase)
  }
  parts.push(`J${summary.round}`)
  return parts.join(' · ')
}

/** `4 / 6`: active reported matches over active matches (ignored matches count in neither). */
export function progressText(summary: Pick<MatchDaySummary, 'reportedMatches' | 'totalMatches'>): string {
  return `${summary.reportedMatches} / ${summary.totalMatches}`
}

export function postponedCount(summary: Pick<MatchDaySummary, 'matchCounts'>): number {
  return summary.matchCounts.POSTPONED
}

/** `2 postponed`, `1 ignored`, or both; empty when neither applies. */
export function suffixText(summary: Pick<MatchDaySummary, 'matchCounts' | 'ignoredMatches'>): string {
  const parts: string[] = []
  if (postponedCount(summary) > 0) {
    parts.push(`${postponedCount(summary)} postponed`)
  }
  if (summary.ignoredMatches > 0) {
    parts.push(`${summary.ignoredMatches} ignored`)
  }
  return parts.join(', ')
}

/** Text for screen readers and tooltips: completion, state, counts. */
export function entryDescription(summary: MatchDaySummary): string {
  const suffix = suffixText(summary)
  return [
    entryLabel(summary),
    COMPLETION_LABELS[summary.completion],
    `${STATE_LABELS[summary.state].toLowerCase()}`,
    `${progressText(summary)} reported`,
    ...(suffix === '' ? [] : [suffix]),
  ].join(', ')
}
