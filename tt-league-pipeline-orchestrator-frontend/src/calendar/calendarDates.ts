// Calendar dates are ISO `YYYY-MM-DD` strings handled as plain calendar dates (no time zone, no daylight saving
// shift): every computation goes through UTC. Weeks start on Monday.

export type CalendarView = 'month' | 'week'

export interface DateRange {
  readonly from: string
  readonly to: string
}

const DAY_MS = 86_400_000
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

export const MONTH_NAMES: readonly string[] = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

/** Monday first. */
export const WEEKDAY_NAMES: readonly string[] = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function pad(value: number, width = 2): string {
  return String(value).padStart(width, '0')
}

function toIso(time: number): string {
  const date = new Date(time)
  return `${pad(date.getUTCFullYear(), 4)}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

function toTime(iso: string): number {
  const match = DATE_PATTERN.exec(iso)
  if (match === null) {
    throw new Error(`Invalid calendar date "${iso}"`)
  }
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

export function isValidIsoDate(value: string): boolean {
  const match = DATE_PATTERN.exec(value)
  if (match === null) {
    return false
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

export function addDays(iso: string, days: number): string {
  return toIso(toTime(iso) + days * DAY_MS)
}

/** The Monday of the week that contains the date. */
export function weekStart(iso: string): string {
  const weekday = (new Date(toTime(iso)).getUTCDay() + 6) % 7
  return addDays(iso, -weekday)
}

function firstOfMonth(iso: string): string {
  return `${iso.slice(0, 7)}-01`
}

function lastOfMonth(iso: string): string {
  const [year, month] = [Number(iso.slice(0, 4)), Number(iso.slice(5, 7))]
  return toIso(Date.UTC(year, month, 0))
}

export function sameMonth(a: string, b: string): boolean {
  return a.slice(0, 7) === b.slice(0, 7)
}

/** Whole weeks (Monday to Sunday) that cover the month of the anchor date. */
export function monthGrid(anchor: string): readonly (readonly string[])[] {
  const start = weekStart(firstOfMonth(anchor))
  const end = addDays(weekStart(lastOfMonth(anchor)), 6)
  const weeks: string[][] = []
  for (let day = start; day <= end; day = addDays(day, 7)) {
    weeks.push(Array.from({ length: 7 }, (_, offset) => addDays(day, offset)))
  }
  return weeks
}

export function weekDays(anchor: string): readonly string[] {
  const start = weekStart(anchor)
  return Array.from({ length: 7 }, (_, offset) => addDays(start, offset))
}

/** The days the view shows, which is what the match-day list is asked for. */
export function visibleRange(view: CalendarView, anchor: string): DateRange {
  if (view === 'week') {
    const days = weekDays(anchor)
    return { from: days[0], to: days[6] }
  }
  const weeks = monthGrid(anchor)
  return { from: weeks[0][0], to: weeks[weeks.length - 1][6] }
}

/** Next or previous period: a week moves seven days; a month keeps the day of the month, clamped to its length. */
export function shift(view: CalendarView, anchor: string, direction: 1 | -1): string {
  if (view === 'week') {
    return addDays(anchor, 7 * direction)
  }
  const [year, month, day] = [Number(anchor.slice(0, 4)), Number(anchor.slice(5, 7)), Number(anchor.slice(8, 10))]
  const target = new Date(Date.UTC(year, month - 1 + direction, 1))
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  return toIso(Date.UTC(target.getUTCFullYear(), target.getUTCMonth(), Math.min(day, lastDay)))
}

/** The browser's local date. */
export function todayIso(now: Date = new Date()): string {
  return `${pad(now.getFullYear(), 4)}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export function dayOfMonth(iso: string): number {
  return Number(iso.slice(8, 10))
}

/** `October 2026`, or `5 Oct – 11 Oct 2026` for a week. */
export function periodTitle(view: CalendarView, anchor: string): string {
  if (view === 'month') {
    return `${MONTH_NAMES[Number(anchor.slice(5, 7)) - 1]} ${anchor.slice(0, 4)}`
  }
  const days = weekDays(anchor)
  const label = (iso: string) => `${dayOfMonth(iso)} ${MONTH_NAMES[Number(iso.slice(5, 7)) - 1].slice(0, 3)}`
  return `${label(days[0])} – ${label(days[6])} ${days[6].slice(0, 4)}`
}

/** Long label for a day, e.g. `Monday 5 October 2026`. */
export function dayLabel(iso: string): string {
  const weekday = (new Date(toTime(iso)).getUTCDay() + 6) % 7
  const names = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
  return `${names[weekday]} ${dayOfMonth(iso)} ${MONTH_NAMES[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`
}
