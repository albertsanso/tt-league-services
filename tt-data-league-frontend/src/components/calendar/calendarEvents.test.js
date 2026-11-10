import { describe, expect, it } from 'vitest'
import {
  countStates, formatMatchDateTime, formatMatchTime, fullCalendarView, groupByDay, groupBySourceAndCompetition, matchDay, sortMatches, summarizeDays, toEvents,
} from './calendarEvents.js'

const match = (id, dateTime, venue = null, extra = {}) => ({
  id, dateTime, venue, homeTeamName: `H${id}`, awayTeamName: `A${id}`, calendarState: 'UPCOMING', ...extra,
})

describe('toEvents', () => {
  it('maps dated matches to FullCalendar events and skips undated ones', () => {
    const events = toEvents([
      match('1', '2026-09-19T18:30:00+02:00', 'Pavelló', { calendarState: 'AWAITING_RESULT' }),
      match('2', null),
    ])

    expect(events).toHaveLength(1)
    expect(events[0]).toMatchObject({
      id: '1',
      start: '2026-09-19T18:30:00+02:00',
      allDay: false,
      title: 'H1 – A1',
      classNames: ['cal-state-awaiting_result'],
    })
    expect(events[0].extendedProps.match.id).toBe('1')
  })
})

describe('matchDay', () => {
  it('uses the Europe/Madrid day', () => {
    expect(matchDay(match('1', '2026-09-19T23:30:00+02:00'))).toBe('2026-09-19')
    expect(matchDay(match('2', '2026-09-19T22:30:00Z'))).toBe('2026-09-20')
    expect(matchDay(match('3', null))).toBeNull()
  })
})

describe('sortMatches', () => {
  const morningLater = match('a', '2026-09-20T10:00:00+02:00', 'Zeta')
  const eveningEarlier = match('b', '2026-09-19T18:00:00+02:00', 'Alfa')
  const morningEarlier = match('c', '2026-09-19T10:00:00+02:00', null)
  const undated = match('d', null, 'Beta')
  const all = [morningLater, eveningEarlier, undated, morningEarlier]

  it('sorts by date and time by default, undated last, without mutating the input', () => {
    const copy = [...all]
    expect(sortMatches(all).map((m) => m.id)).toEqual(['c', 'b', 'a', 'd'])
    expect(all).toEqual(copy)
  })
})

describe('fullCalendarView', () => {
  it('maps the list views to FullCalendar views (week and month are native components)', () => {
    expect(fullCalendarView('day')).toBe('listDay')
    expect(fullCalendarView('jornada')).toBe('listJornada')
  })
})

describe('summarizeDays', () => {
  it('counts the dated matches per Madrid day and per state, in a fixed state order', () => {
    const summary = summarizeDays([
      match('1', '2026-09-19T10:00:00+02:00', null, { calendarState: 'PLAYED' }),
      match('2', '2026-09-19T12:00:00+02:00', null, { calendarState: 'OVERDUE' }),
      match('3', '2026-09-19T13:00:00+02:00', null, { calendarState: 'PLAYED' }),
      match('4', '2026-09-19T22:30:00Z', null, { calendarState: 'UPCOMING' }),
      match('5', null, null, { calendarState: 'UNDATED' }),
    ])

    expect([...summary.keys()]).toEqual(['2026-09-19', '2026-09-20'])
    expect(summary.get('2026-09-19')).toEqual({
      total: 3,
      states: [{ state: 'OVERDUE', count: 1 }, { state: 'PLAYED', count: 2 }],
    })
    expect(summary.get('2026-09-20')).toEqual({ total: 1, states: [{ state: 'UPCOMING', count: 1 }] })
  })

  it('only reports the states the API returned and keeps an unknown one', () => {
    const summary = summarizeDays([match('1', '2026-09-19T10:00:00+02:00', null, { calendarState: 'FUTURE' })])
    expect(summary.get('2026-09-19').states).toEqual([{ state: 'FUTURE', count: 1 }])
  })

  it('has no entry for a day without matches', () => {
    expect(summarizeDays([]).size).toBe(0)
  })
})

describe('groupByDay', () => {
  it('groups dated matches by Madrid day in ascending order and skips undated ones', () => {
    const groups = groupByDay([
      match('c', '2026-09-20T10:00:00+02:00'),
      match('a', '2026-09-19T10:00:00+02:00'),
      match('u', null),
      match('b', '2026-09-19T12:00:00+02:00'),
    ])

    expect(groups.map((group) => group.day)).toEqual(['2026-09-19', '2026-09-20'])
    expect(groups[0].matches.map((m) => m.id)).toEqual(['a', 'b'])
    expect(groups[1].matches.map((m) => m.id)).toEqual(['c'])
  })
})

describe('formatMatchTime and formatMatchDateTime', () => {
  it('format in Europe/Madrid time whatever the browser zone', () => {
    expect(formatMatchTime('2026-09-19T22:30:00Z', 'ca')).toBe('00:30')
    expect(formatMatchTime('2026-01-15T17:30:00Z', 'en')).toBe('18:30')
    expect(formatMatchDateTime('2026-09-19T16:30:00Z', 'ca')).toContain('18:30')
  })

  it('format the date in the app language', () => {
    const value = '2026-09-19T16:30:00+00:00'
    expect(formatMatchDateTime(value, 'ca')).toMatch(/19 de set/)
    expect(formatMatchDateTime(value, 'es')).toMatch(/19 sept/)
    expect(formatMatchDateTime(value, 'en')).toMatch(/19 Sept/)
    expect(formatMatchDateTime(value, 'fr')).toBe(formatMatchDateTime(value, 'ca'))
  })
})

describe('countStates', () => {
  it('counts the matches per state in display order and keeps unknown states last', () => {
    const counts = countStates([
      match('1', null, null, { calendarState: 'PLAYED' }),
      match('2', null, null, { calendarState: 'OVERDUE' }),
      match('3', null, null, { calendarState: 'PLAYED' }),
      match('4', null, null, { calendarState: 'FUTURE' }),
    ])

    expect(counts).toEqual({
      total: 4,
      states: [{ state: 'OVERDUE', count: 1 }, { state: 'PLAYED', count: 2 }, { state: 'FUTURE', count: 1 }],
    })
    expect(countStates([])).toEqual({ total: 0, states: [] })
  })
})

describe('groupBySourceAndCompetition', () => {
  const entry = (id, source, competition, time, state = 'UPCOMING') => match(
    id, `2026-09-19T${time}:00+02:00`, null, { source, competition, calendarState: state },
  )

  it('groups by source in the platform order, then by competition alphabetically, with counts', () => {
    const groups = groupBySourceAndCompetition([
      entry('1', 'BCNESA', 'tercera', '10:00'),
      entry('2', 'RFETM', 'segona', '10:00', 'PLAYED'),
      entry('3', 'RFETM', 'primera', '12:00', 'OVERDUE'),
      entry('4', 'RFETM', 'primera', '09:00'),
      entry('5', 'FCTT', 'tercera', '10:00'),
      entry('6', 'ZZZ', 'x', '10:00'),
    ])

    expect(groups.map((group) => group.source)).toEqual(['RFETM', 'FCTT', 'BCNESA', 'ZZZ'])
    const rfetm = groups[0]
    expect(rfetm.total).toBe(3)
    expect(rfetm.states).toEqual([
      { state: 'OVERDUE', count: 1 }, { state: 'UPCOMING', count: 1 }, { state: 'PLAYED', count: 1 },
    ])
    expect(rfetm.competitions.map((group) => group.competition)).toEqual(['primera', 'segona'])
    expect(rfetm.competitions[0]).toMatchObject({ total: 2 })
  })

  it('orders the matches of a competition by time', () => {
    const [group] = groupBySourceAndCompetition([
      entry('late', 'FCTT', 'tercera', '20:00'),
      entry('early', 'FCTT', 'tercera', '09:00'),
    ])

    expect(group.competitions[0].matches.map((m) => m.id)).toEqual(['early', 'late'])
  })

  it('uses the fallback source when the matches carry none, and labels missing values with empty strings', () => {
    const groups = groupBySourceAndCompetition([
      match('1', '2026-09-19T10:00:00+02:00'),
      entry('2', undefined, 'tercera', '10:00'),
    ], 'FCTT')

    expect(groups.map((group) => group.source)).toEqual(['FCTT'])
    expect(groups[0].competitions.map((group) => group.competition)).toEqual(['tercera', ''])
    expect(groupBySourceAndCompetition([match('3', '2026-09-19T10:00:00+02:00')])[0].source).toBe('')
  })

  it('returns no groups for no matches', () => {
    expect(groupBySourceAndCompetition([])).toEqual([])
  })
})
