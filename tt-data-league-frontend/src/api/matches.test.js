import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearMatchOverdueMark,
  getSeasonCalendar,
  markMatchOverdue,
} from './matches.js'

const jsonHeaders = { get: () => 'application/json' }

function calendarPayload() {
  return {
    source: 'FCTT',
    season: '2026-2027',
    competition: 'tercera',
    today: '2026-09-08',
    overdueGraceDays: 7,
    groups: [{ groupNumber: 1, rounds: [{ matches: [] }] }],
  }
}

describe('season calendar API boundary', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('requires source, season and competition', () => {
    expect(() => getSeasonCalendar({ source: '', season: '1', competition: 'c' })).toThrow()
    expect(() => getSeasonCalendar({ source: 'FCTT', season: '', competition: 'c' })).toThrow()
    expect(() => getSeasonCalendar({ source: 'FCTT', season: '1', competition: '' })).toThrow()
  })

  it('builds the calendar query string and omits empty optional filters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, headers: jsonHeaders, json: async () => calendarPayload(),
    })

    await getSeasonCalendar(
      { source: ' FCTT ', season: '2026-2027', competition: 'tercera', group: '', round: '' },
      'token',
      new AbortController().signal,
    )

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/calendar?source=FCTT&season=2026-2027&competition=tercera',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('includes group and round when selected', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, headers: jsonHeaders, json: async () => calendarPayload(),
    })

    await getSeasonCalendar(
      { source: 'FCTT', season: '2026-2027', competition: 'tercera', group: '1', round: '2' },
      'token',
    )

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/calendar?source=FCTT&season=2026-2027&competition=tercera&group=1&round=2',
      expect.anything(),
    )
  })

  it('rejects an invalid calendar shape with a 502', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, headers: jsonHeaders, json: async () => ({ source: 'FCTT' }),
    })

    await expect(getSeasonCalendar(
      { source: 'FCTT', season: '2026-2027', competition: 'tercera' }, 'token',
    )).rejects.toMatchObject({ status: 502 })
  })

  it('marks overdue with a PUT and encodes the id', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, headers: jsonHeaders, json: async () => null,
    })

    await markMatchOverdue('match/1', 'token', undefined, vi.fn())

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/match%2F1/overdue-mark',
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('clears the overdue mark with a DELETE', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, headers: jsonHeaders, json: async () => null,
    })

    await clearMatchOverdueMark('match-1', 'token', undefined, vi.fn())

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/match/match-1/overdue-mark',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})