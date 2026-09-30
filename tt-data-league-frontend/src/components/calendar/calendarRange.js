import {
  addDays, addMonths, addWeeks, endOfMonth, format, isValid, parse, startOfMonth, startOfWeek,
} from 'date-fns'
import { ca, enGB, es } from 'date-fns/locale'

export const VIEWS = ['day', 'week', 'month', 'jornada']
export const DEFAULT_VIEW = 'week'

const DATE_FORMAT = 'yyyy-MM-dd'
const WEEK_OPTIONS = { weekStartsOn: 1 }
const LOCALES = { ca, es, en: enGB }

export function dateFnsLocale(language) {
  return LOCALES[String(language ?? '').slice(0, 2)] ?? ca
}

export function toIsoDate(date) {
  return format(date, DATE_FORMAT)
}

export function todayIso() {
  return toIsoDate(new Date())
}

export function normalizeView(value) {
  return VIEWS.includes(value) ? value : DEFAULT_VIEW
}

function parseIso(value) {
  if (typeof value !== 'string') return null
  const parsed = parse(value, DATE_FORMAT, new Date())
  return isValid(parsed) && toIsoDate(parsed) === value ? parsed : null
}

/** A valid `YYYY-MM-DD` anchor, or today when the value is missing or malformed. */
export function normalizeDate(value) {
  return parseIso(value) ? value : todayIso()
}

/** Half-open `{ from, to }` (exclusive `to`) of the days a view covers; `null` for the jornada view. */
export function rangeFor(view, anchorDate) {
  const anchor = parseIso(normalizeDate(anchorDate))
  if (view === 'day') {
    return { from: toIsoDate(anchor), to: toIsoDate(addDays(anchor, 1)) }
  }
  if (view === 'week') {
    const start = startOfWeek(anchor, WEEK_OPTIONS)
    return { from: toIsoDate(start), to: toIsoDate(addDays(start, 7)) }
  }
  if (view === 'month') {
    const start = startOfWeek(startOfMonth(anchor), WEEK_OPTIONS)
    const lastWeek = startOfWeek(endOfMonth(anchor), WEEK_OPTIONS)
    return { from: toIsoDate(start), to: toIsoDate(addDays(lastWeek, 7)) }
  }
  return null
}

/** Moves the anchor one period (`direction` is `-1` or `1`); the jornada view has no date period. */
export function shift(view, anchorDate, direction) {
  const anchor = parseIso(normalizeDate(anchorDate))
  if (view === 'day') return toIsoDate(addDays(anchor, direction))
  if (view === 'week') return toIsoDate(addWeeks(anchor, direction))
  if (view === 'month') return toIsoDate(addMonths(anchor, direction))
  return toIsoDate(anchor)
}

export function titleFor(view, anchorDate, language) {
  const locale = dateFnsLocale(language)
  const anchor = parseIso(normalizeDate(anchorDate))
  if (view === 'day') return format(anchor, 'PPPP', { locale })
  if (view === 'week') {
    const start = startOfWeek(anchor, WEEK_OPTIONS)
    const end = addDays(start, 6)
    return `${format(start, 'd MMM', { locale })} – ${format(end, 'd MMM yyyy', { locale })}`
  }
  if (view === 'month') return format(anchor, 'LLLL yyyy', { locale })
  return ''
}
