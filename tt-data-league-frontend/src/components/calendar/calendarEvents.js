export const SORTS = ['date', 'time', 'venue']
export const DEFAULT_SORT = 'date'

export const STATE_TONES = {
  PLAYED: 'success',
  UPCOMING: 'subtle',
  AWAITING_RESULT: 'warning',
  UNDATED: 'subtle',
  OVERDUE: 'error',
  POSTPONED: 'warning',
}

/** The FullCalendar view that renders one of our views (`compact` favours lists on small screens). */
export function fullCalendarView(view, compact = false) {
  if (view === 'day') return 'listDay'
  if (view === 'week') return compact ? 'listWeek' : 'timeGridWeek'
  if (view === 'month') return 'dayGridMonth'
  return 'listJornada'
}

const MADRID = 'Europe/Madrid'
const timeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: MADRID, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})
const dayFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: MADRID, year: 'numeric', month: '2-digit', day: '2-digit',
})

export function normalizeSort(value) {
  return SORTS.includes(value) ? value : DEFAULT_SORT
}

export function matchTitle(match) {
  return `${match.homeTeamName ?? '—'} – ${match.awayTeamName ?? '—'}`
}

/** The `YYYY-MM-DD` day of a match in the competition time zone, or `null` when undated. */
export function matchDay(match) {
  return match.dateTime ? dayFormat.format(new Date(match.dateTime)) : null
}

function minutesOfDay(match) {
  const [hours, minutes] = timeFormat.format(new Date(match.dateTime)).split(':').map(Number)
  return hours * 60 + minutes
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

const instant = (match) => (match.dateTime ? new Date(match.dateTime).getTime() : Number.POSITIVE_INFINITY)

function compare(left, right) {
  if (left === right) return 0
  return left < right ? -1 : 1
}

const byDateTime = (a, b) => compare(instant(a), instant(b))

/** Presentation-only ordering of a copy of `matches` (never mutates the input). */
export function sortMatches(matches, sort = DEFAULT_SORT) {
  const copy = [...matches]
  if (sort === 'time') {
    return copy.sort((a, b) => {
      const left = a.dateTime ? minutesOfDay(a) : Number.POSITIVE_INFINITY
      const right = b.dateTime ? minutesOfDay(b) : Number.POSITIVE_INFINITY
      return compare(left, right) || byDateTime(a, b)
    })
  }
  if (sort === 'venue') {
    return copy.sort((a, b) => {
      if (!a.venue && b.venue) return 1
      if (a.venue && !b.venue) return -1
      const order = a.venue && b.venue ? a.venue.localeCompare(b.venue) : 0
      return order || byDateTime(a, b)
    })
  }
  return copy.sort(byDateTime)
}
