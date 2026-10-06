import type { PolicyLevel, PollingMode } from '../api/types'

const DURATION_PATTERN = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/

/** Whole seconds of a positive ISO-8601 duration (days, hours, minutes, seconds); null when it is not one. */
export function parseIsoDuration(value: string): number | null {
  const text = value.trim()
  const match = DURATION_PATTERN.exec(text)
  if (match === null || text === 'P' || text.endsWith('T')) {
    return null
  }
  const [days, hours, minutes, seconds] = match.slice(1).map((part) => (part === undefined ? 0 : Number(part)))
  return ((days * 24 + hours) * 60 + minutes) * 60 + seconds
}

/** `PT2H` -> `2 h`, `PT168H` -> `7 d`, `PT90M` -> `1 h 30 min`; text that is not a duration is returned as is. */
export function formatIsoDuration(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const total = parseIsoDuration(value)
  if (total === null) {
    return value
  }
  if (total === 0) {
    return '0 s'
  }
  const parts: string[] = []
  const days = Math.floor(total / 86_400)
  const hours = Math.floor((total % 86_400) / 3_600)
  const minutes = Math.floor((total % 3_600) / 60)
  const seconds = total % 60
  if (days > 0) {
    parts.push(`${days} d`)
  }
  if (hours > 0) {
    parts.push(`${hours} h`)
  }
  if (minutes > 0) {
    parts.push(`${minutes} min`)
  }
  if (seconds > 0) {
    parts.push(`${seconds} s`)
  }
  return parts.join(' ')
}

export const LEVEL_LABELS: Readonly<Record<PolicyLevel, string>> = {
  MATCH_DAY: 'Match day',
  DAY_AFTER: 'Day after',
  DAYS_2_TO_7: 'Days 2 to 7',
  OPEN: 'Open',
  OVERDUE: 'Overdue',
  STOPPED: 'Stopped',
  FULL_REFRESH: 'Full refresh',
}

/** The lookback of a policy as sent by the server; the browser does not decide which match days it covers. */
export function lookbackLabel(recentMatchDays: number): string {
  return recentMatchDays === 1 ? 'Last match day per group' : `Last ${recentMatchDays} match days per group`
}

export function modeLabel(mode: PollingMode, cron: string | null): string {
  switch (mode) {
    case 'ADAPTIVE':
      return 'Adaptive'
    case 'CRON':
      return cron === null ? 'Cron' : `Cron ${cron}`
    case 'NONE':
      return 'Not scheduled'
  }
}
