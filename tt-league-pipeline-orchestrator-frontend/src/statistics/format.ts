const DASH = '—'

/** Server seconds shown as hours with one decimal (`5400` -> `1.5 h`); the browser never derives a figure. */
export function formatHours(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || seconds < 0) {
    return DASH
  }
  return `${(seconds / 3600).toFixed(1)} h`
}

/** Hours with one decimal as a number, for chart axes. */
export function toHours(seconds: number | null | undefined): number | null {
  if (seconds === null || seconds === undefined || seconds < 0) {
    return null
  }
  return Math.round((seconds / 3600) * 10) / 10
}

/** `10-05` for a `YYYY-MM-DD` date, to keep chart ticks short. */
export function shortDate(date: string): string {
  return date.length === 10 ? date.slice(5) : date
}

/** A count, or a dash when the server sent no value. */
export function formatCount(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : String(value)
}

/** Share of the active matches that are reported, as a whole percent (the one figure the plan lets the table show). */
export function formatPercent(part: number, whole: number): string {
  return whole <= 0 ? DASH : `${Math.round((part / whole) * 100)}%`
}
