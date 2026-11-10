import { CALENDAR_SOURCES } from '../../api/calendarSources.js'

export const STATE_TONES = {
  PLAYED: 'success',
  UPCOMING: 'subtle',
  AWAITING_RESULT: 'warning',
  UNDATED: 'subtle',
  OVERDUE: 'error',
  POSTPONED: 'warning',
}

/** The FullCalendar list view that renders the day and jornada views (week and month are native components). */
export function fullCalendarView(view) {
  return view === 'day' ? 'listDay' : 'listJornada'
}

/** States in display order: the ones that need attention first. */
export const STATE_ORDER = ['OVERDUE', 'AWAITING_RESULT', 'POSTPONED', 'UPCOMING', 'PLAYED', 'UNDATED']

const MADRID = 'Europe/Madrid'
const INTL_LOCALES = { ca: 'ca', es: 'es', en: 'en-GB' }
const dayFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: MADRID, year: 'numeric', month: '2-digit', day: '2-digit',
})

function intlLocale(language) {
  return INTL_LOCALES[String(language ?? '').slice(0, 2)] ?? INTL_LOCALES.ca
}

/** The match time (`HH:mm`) in the competition time zone and the app language. */
export function formatMatchTime(value, language) {
  return new Intl.DateTimeFormat(intlLocale(language), {
    timeZone: MADRID, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(value))
}

/** The match date and time in the competition time zone and the app language. */
export function formatMatchDateTime(value, language) {
  return new Intl.DateTimeFormat(intlLocale(language), {
    timeZone: MADRID, day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(value))
}

export function matchTitle(match) {
  return `${match.homeTeamName ?? '—'} – ${match.awayTeamName ?? '—'}`
}

/** The `YYYY-MM-DD` day of a match in the competition time zone, or `null` when undated. */
export function matchDay(match) {
  return match.dateTime ? dayFormat.format(new Date(match.dateTime)) : null
}

/** FullCalendar `EventInput` list for the dated matches. */
export function toEvents(matches) {
  return matches
    .filter((match) => match.dateTime)
    .map((match) => ({
      id: match.id,
      start: match.dateTime,
      allDay: false,
      title: matchTitle(match),
      classNames: [`cal-state-${String(match.calendarState ?? '').toLowerCase()}`],
      extendedProps: { match },
    }))
}

/**
 * Presentation-only count of `matches` by the `calendarState` the API returned: the total and the
 * states present, following `STATE_ORDER` (a state outside it follows, in arrival order).
 */
export function countStates(matches) {
  const byState = new Map()
  matches.forEach((match) => byState.set(match.calendarState, (byState.get(match.calendarState) ?? 0) + 1))
  const known = STATE_ORDER.filter((state) => byState.has(state))
  const others = [...byState.keys()].filter((state) => !STATE_ORDER.includes(state))
  return {
    total: matches.length,
    states: [...known, ...others].map((state) => ({ state, count: byState.get(state) })),
  }
}

/** Presentation-only counts of the dated matches per Europe/Madrid day, by `calendarState`. */
export function summarizeDays(matches) {
  const summary = new Map()
  groupByDay(matches).forEach((group) => summary.set(group.day, countStates(group.matches)))
  return summary
}

/** The dated matches grouped by Europe/Madrid day, in ascending day order. */
export function groupByDay(matches) {
  const groups = new Map()
  matches.forEach((match) => {
    const day = matchDay(match)
    if (!day) return
    if (groups.has(day)) groups.get(day).push(match)
    else groups.set(day, [match])
  })
  return [...groups.keys()].sort().map((day) => ({ day, matches: groups.get(day) }))
}

const instant = (match) => (match.dateTime ? new Date(match.dateTime).getTime() : Number.POSITIVE_INFINITY)

function compare(left, right) {
  if (left === right) return 0
  return left < right ? -1 : 1
}

const byDateTime = (a, b) => compare(instant(a), instant(b))

/** Presentation-only date and time ordering of a copy of `matches` (never mutates the input; undated last). */
export function sortMatches(matches) {
  return [...matches].sort(byDateTime)
}

function compareText(left, right) {
  return left.localeCompare(right)
}

function sourceRank(source) {
  const index = CALENDAR_SOURCES.indexOf(source)
  return index < 0 ? CALENDAR_SOURCES.length : index
}

/**
 * Presentation-only grouping of `matches` by source and then by competition, with the counts per state.
 * A match's source is `match.source`, or `fallbackSource` when the response carries none (a single-source
 * request); a missing source or competition is the empty string, which the UI labels. Sources follow
 * `CALENDAR_SOURCES` and then alphabetical order; competitions are alphabetical, a missing one last;
 * the matches of a competition are in time order.
 */
export function groupBySourceAndCompetition(matches, fallbackSource = '') {
  const bySource = new Map()
  matches.forEach((match) => {
    const source = match.source || fallbackSource || ''
    const competition = match.competition || ''
    if (!bySource.has(source)) bySource.set(source, new Map())
    const competitions = bySource.get(source)
    if (!competitions.has(competition)) competitions.set(competition, [])
    competitions.get(competition).push(match)
  })
  return [...bySource.keys()]
    .sort((a, b) => sourceRank(a) - sourceRank(b) || compareText(a, b))
    .map((source) => {
      const competitions = bySource.get(source)
      const groups = [...competitions.keys()]
        .sort((a, b) => (a === '' ? 1 : 0) - (b === '' ? 1 : 0) || compareText(a, b))
        .map((competition) => {
          const sorted = sortMatches(competitions.get(competition))
          return { competition, ...countStates(sorted), matches: sorted }
        })
      return { source, ...countStates(groups.flatMap((group) => group.matches)), competitions: groups }
    })
}
