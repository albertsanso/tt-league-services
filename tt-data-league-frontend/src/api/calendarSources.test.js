import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCalendarOptions, getCalendarRangeAcrossSources } from './calendarSources.js'
import { getCalendarRange, getMatchOptions } from './matches.js'

vi.mock('./matches.js', () => ({
  getCalendarRange: vi.fn(),
  getMatchOptions: vi.fn(),
}))

const signal = new AbortController().signal
const onUnauthorized = vi.fn()

const range = (source, matches, facets = {}) => ({
  source,
  season: '2026-2027',
  from: '2026-09-14',
  to: '2026-09-21',
  today: '2026-09-16',
  overdueGraceDays: 7,
  matches,
  facets: { competitions: [], groups: [], teams: [], ...facets },
})

const filters = { season: '2026-2027', from: '2026-09-14', to: '2026-09-21', competition: '', group: '', team: '' }

describe('calendar across sources', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads options for each source and merges seasons newest first and competitions alphabetically', async () => {
    getMatchOptions.mockImplementation((source) => Promise.resolve({
      RFETM: { seasons: ['2025-2026'], competitions: ['primera', 'tercera'] },
      FCTT: { seasons: ['2026-2027', '2025-2026'], competitions: ['tercera'] },
      BCNESA: { seasons: ['2026-2027'], competitions: ['segunda'] },
    }[source]))

    const options = await getCalendarOptions('', '', 'token', signal, onUnauthorized)

    expect(getMatchOptions).toHaveBeenCalledTimes(3)
    expect(getMatchOptions).toHaveBeenCalledWith('RFETM', '', 'token', signal, onUnauthorized)
    expect(options).toEqual({
      seasons: ['2026-2027', '2025-2026'],
      competitions: ['primera', 'segunda', 'tercera'],
    })
  })

  it('passes a single source through unchanged', async () => {
    getMatchOptions.mockResolvedValue({ seasons: ['2026-2027'], competitions: ['tercera'] })

    const options = await getCalendarOptions('FCTT', '2026-2027', 'token', signal, onUnauthorized)

    expect(getMatchOptions).toHaveBeenCalledTimes(1)
    expect(getMatchOptions).toHaveBeenCalledWith('FCTT', '2026-2027', 'token', signal, onUnauthorized)
    expect(options).toEqual({ seasons: ['2026-2027'], competitions: ['tercera'] })
  })

  it('treats malformed option lists as empty', async () => {
    getMatchOptions.mockResolvedValue({ seasons: 'nope', competitions: null })

    expect(await getCalendarOptions('FCTT', '', 'token', signal, onUnauthorized))
      .toEqual({ seasons: [], competitions: [] })
  })

  it('requests one range per source with the same filters and merges the response', async () => {
    getCalendarRange.mockImplementation(({ source }) => Promise.resolve({
      RFETM: range('RFETM',
        [{ id: 'b', dateTime: '2026-09-19T20:00:00+02:00' }, { id: 'u', dateTime: null }],
        {
          competitions: ['tercera'],
          groups: [2, null],
          teams: [{ teamId: 'r2', name: 'Zeta', competition: 'tercera' }],
        }),
      FCTT: range('FCTT',
        [{ id: 'a', dateTime: '2026-09-19T20:00:00+02:00' }, { id: 'c', dateTime: '2026-09-18T10:00:00+02:00' }],
        {
          competitions: ['segunda', 'tercera'],
          groups: [1, 2],
          teams: [{ teamId: 'f1', name: 'Alfa', competition: 'segunda' }],
        }),
      BCNESA: range('BCNESA', [], { groups: [null] }),
    }[source]))

    const merged = await getCalendarRangeAcrossSources({ ...filters, competition: 'tercera' }, 'token', signal, onUnauthorized)

    expect(getCalendarRange.mock.calls.map(([call]) => call.source)).toEqual(['RFETM', 'FCTT', 'BCNESA'])
    getCalendarRange.mock.calls.forEach(([call]) => expect(call).toMatchObject({ ...filters, competition: 'tercera' }))
    expect(merged).toMatchObject({
      source: '', season: '2026-2027', from: '2026-09-14', to: '2026-09-21', today: '2026-09-16', overdueGraceDays: 7,
    })
    expect(merged.matches.map((match) => [match.id, match.source])).toEqual([
      ['c', 'FCTT'], ['a', 'FCTT'], ['b', 'RFETM'], ['u', 'RFETM'],
    ])
    expect(merged.facets.competitions).toEqual(['segunda', 'tercera'])
    expect(merged.facets.groups).toEqual([1, 2, null])
    expect(merged.facets.teams).toEqual([
      { teamId: 'f1', name: 'Alfa', competition: 'segunda', source: 'FCTT' },
      { teamId: 'r2', name: 'Zeta', competition: 'tercera', source: 'RFETM' },
    ])
  })

  it('rejects the whole call when a single source fails', async () => {
    getCalendarRange.mockImplementation(({ source }) => (source === 'FCTT'
      ? Promise.reject(Object.assign(new Error('down'), { status: 500 }))
      : Promise.resolve(range(source, []))))

    await expect(getCalendarRangeAcrossSources(filters, 'token', signal, onUnauthorized)).rejects.toThrow('down')
  })
})
