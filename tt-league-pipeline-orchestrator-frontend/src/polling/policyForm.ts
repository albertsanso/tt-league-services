import type { PollingPolicy, PollingPolicyRequest } from '../api/types'
import { parseIsoDuration } from './format'

export const DURATION_FIELDS = [
  'matchDay',
  'matchDayStartOffset',
  'dayAfter',
  'daysTwoToSeven',
  'open',
  'overdue',
  'fullRefresh',
] as const
export const COUNT_FIELDS = ['overdueStopAfterDays', 'noChangeThreshold', 'recentMatchDays'] as const

export type DurationField = (typeof DURATION_FIELDS)[number]
export type CountField = (typeof COUNT_FIELDS)[number]
export type PolicyField = DurationField | CountField

/** The form keeps what was typed: ISO-8601 durations and integers as text. */
export type PolicyFormValues = Readonly<Record<PolicyField, string>>
export type PolicyFormErrors = Readonly<Partial<Record<PolicyField, string>>>

export const FIELD_LABELS: Readonly<Record<PolicyField, string>> = {
  matchDay: 'Match day interval',
  matchDayStartOffset: 'Match day starts after',
  dayAfter: 'Day after interval',
  daysTwoToSeven: 'Days 2 to 7 interval',
  open: 'Open interval',
  overdue: 'Overdue interval',
  overdueStopAfterDays: 'Stop overdue units after (days)',
  fullRefresh: 'Full refresh interval',
  noChangeThreshold: 'No-change threshold (runs)',
  recentMatchDays: 'Match days per group (lookback)',
}

export function toFormValues(policy: PollingPolicy): PolicyFormValues {
  return {
    matchDay: policy.matchDay,
    matchDayStartOffset: policy.matchDayStartOffset,
    dayAfter: policy.dayAfter,
    daysTwoToSeven: policy.daysTwoToSeven,
    open: policy.open,
    overdue: policy.overdue,
    fullRefresh: policy.fullRefresh,
    overdueStopAfterDays: String(policy.overdueStopAfterDays),
    noChangeThreshold: String(policy.noChangeThreshold),
    recentMatchDays: String(policy.recentMatchDays),
  }
}

function integerOf(text: string): number | null {
  return /^\d+$/.test(text.trim()) ? Number(text.trim()) : null
}

/**
 * Mirrors `PollingSettings` of the orchestrator: positive durations, `matchDay <= dayAfter <= daysTwoToSeven <= open <=
 * fullRefresh`, `overdue <= fullRefresh` and counts of at least 1. It only saves a round trip: the server still decides.
 */
export function validatePolicyForm(values: PolicyFormValues): PolicyFormErrors {
  const errors: Partial<Record<PolicyField, string>> = {}
  const seconds: Partial<Record<DurationField, number>> = {}
  for (const field of DURATION_FIELDS) {
    const parsed = parseIsoDuration(values[field])
    if (parsed === null) {
      errors[field] = 'Use an ISO-8601 duration such as PT2H or P7D'
    } else if (parsed <= 0) {
      errors[field] = 'Must be a positive duration'
    } else {
      seconds[field] = parsed
    }
  }
  const chain: readonly (readonly [DurationField, DurationField])[] = [
    ['matchDay', 'dayAfter'],
    ['dayAfter', 'daysTwoToSeven'],
    ['daysTwoToSeven', 'open'],
    ['open', 'fullRefresh'],
  ]
  for (const [shorter, longer] of chain) {
    const a = seconds[shorter]
    const b = seconds[longer]
    if (a !== undefined && b !== undefined && a > b && errors[shorter] === undefined) {
      errors[shorter] = `Must not exceed the ${FIELD_LABELS[longer].toLowerCase()}`
    }
  }
  const overdue = seconds.overdue
  const full = seconds.fullRefresh
  if (overdue !== undefined && full !== undefined && overdue > full && errors.overdue === undefined) {
    errors.overdue = 'Must not exceed the full refresh interval'
  }
  for (const field of COUNT_FIELDS) {
    const count = integerOf(values[field])
    if (count === null || count < 1) {
      errors[field] = 'Must be a whole number of at least 1'
    }
  }
  return errors
}

/** The request for a form that passed {@link validatePolicyForm}. */
export function toRequest(values: PolicyFormValues, version: number): PollingPolicyRequest {
  return {
    matchDay: values.matchDay.trim(),
    matchDayStartOffset: values.matchDayStartOffset.trim(),
    dayAfter: values.dayAfter.trim(),
    daysTwoToSeven: values.daysTwoToSeven.trim(),
    open: values.open.trim(),
    overdue: values.overdue.trim(),
    overdueStopAfterDays: Number(values.overdueStopAfterDays.trim()),
    fullRefresh: values.fullRefresh.trim(),
    noChangeThreshold: Number(values.noChangeThreshold.trim()),
    recentMatchDays: Number(values.recentMatchDays.trim()),
    version,
  }
}
