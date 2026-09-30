import { describe, expect, it } from 'vitest'
import { matchDay, sortMatches, toEvents } from './calendarEvents.js'

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

  it('sorts by time of day in Europe/Madrid, then by date', () => {
    expect(sortMatches(all, 'time').map((m) => m.id)).toEqual(['c', 'a', 'b', 'd'])
    // 23:30 UTC is 01:30 the next day in Madrid, so it sorts before a 10:00 match.
    const lateUtc = match('e', '2026-09-19T23:30:00Z')
    expect(sortMatches([morningEarlier, lateUtc], 'time').map((m) => m.id)).toEqual(['e', 'c'])
  })

  it('sorts by venue with missing venues last, then by date', () => {
    expect(sortMatches(all, 'venue').map((m) => m.id)).toEqual(['b', 'd', 'a', 'c'])
    const sameVenue = [
      match('x', '2026-09-20T10:00:00+02:00', 'Alfa'),
      match('y', '2026-09-19T10:00:00+02:00', 'Alfa'),
    ]
    expect(sortMatches(sameVenue, 'venue').map((m) => m.id)).toEqual(['y', 'x'])
  })
})
