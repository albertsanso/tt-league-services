import { makeStatus } from '../test/pollingFixtures'
import { parsePollingFilters, resolvePollingFilters, serializePollingFilters } from './pollingFilters'

describe('parsePollingFilters', () => {
  it('reads a source and a season', () => {
    expect(parsePollingFilters(new URLSearchParams('source=FCTT&season=2026-2027'))).toEqual({
      source: 'FCTT',
      season: '2026-2027',
      errors: [],
    })
  })

  it('leaves missing filters empty', () => {
    expect(parsePollingFilters(new URLSearchParams(''))).toEqual({ source: null, season: null, errors: [] })
  })

  it('ignores an unknown source and an invalid season with a message each', () => {
    const filters = parsePollingFilters(new URLSearchParams('source=XYZ&season=2026-2028'))
    expect(filters.source).toBeNull()
    expect(filters.season).toBeNull()
    expect(filters.errors).toEqual(['Unknown source "XYZ" was ignored.', 'Invalid season "2026-2028" was ignored.'])
  })
})

describe('serializePollingFilters', () => {
  it('writes only what is chosen', () => {
    expect(serializePollingFilters({ source: 'BCNESA', season: null }).toString()).toBe('source=BCNESA')
    expect(serializePollingFilters({ source: null, season: '2026-2027' }).toString()).toBe('season=2026-2027')
    expect(serializePollingFilters({ source: null, season: null }).toString()).toBe('')
  })
})

describe('resolvePollingFilters', () => {
  it('defaults to the schedule season and the first adaptive source', () => {
    expect(resolvePollingFilters({ source: null, season: null }, makeStatus())).toEqual({
      source: 'FCTT',
      season: '2026-2027',
    })
  })

  it('falls back to the first source when none is adaptive, and to no season when none is scheduled', () => {
    const status = makeStatus({
      season: null,
      zone: null,
      sources: [
        { source: 'RFETM', mode: 'NONE', cron: null },
        { source: 'BCNESA', mode: 'NONE', cron: null },
        { source: 'FCTT', mode: 'NONE', cron: null },
      ],
    })
    expect(resolvePollingFilters({ source: null, season: null }, status)).toEqual({ source: 'RFETM', season: null })
  })

  it('keeps the chosen filters', () => {
    expect(resolvePollingFilters({ source: 'RFETM', season: '2025-2026' }, makeStatus())).toEqual({
      source: 'RFETM',
      season: '2025-2026',
    })
  })

  it('works before the status is known', () => {
    expect(resolvePollingFilters({ source: null, season: null }, null)).toEqual({ source: 'RFETM', season: null })
  })
})
