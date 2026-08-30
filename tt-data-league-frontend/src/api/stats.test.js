import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCommunityStatistics, normalizeCommunityStatsResponse } from './stats.js'

describe('stats API boundary', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('normalizes a well-formed community statistics response', () => {
    const stats = normalizeCommunityStatsResponse({
      players: { total: 1248, currentSeasonCount: 86 },
      clubs: { total: 186, currentSeasonCount: 9 },
      matches: { total: 8432, currentSeasonCount: 1257 },
      season: { name: '2025-2026', status: 'IN_PROGRESS' },
    })

    expect(stats.players).toEqual({ total: 1248 })
    expect(stats.season).toEqual({ name: '2025-2026', status: 'IN_PROGRESS' })
  })

  it('normalizes an unavailable season with a null name', () => {
    const stats = normalizeCommunityStatsResponse({
      players: { total: 0, currentSeasonCount: 0 },
      clubs: { total: 0, currentSeasonCount: 0 },
      matches: { total: 0, currentSeasonCount: 0 },
      season: { name: null, status: 'UNAVAILABLE' },
    })

    expect(stats.season).toEqual({ name: null, status: 'UNAVAILABLE' })
  })

  it('rejects a response missing a count section', () => {
    expect(() => normalizeCommunityStatsResponse({
      players: { total: 1, currentSeasonCount: 0 },
      clubs: { total: 1, currentSeasonCount: 0 },
      season: { name: '2025-2026', status: 'IN_PROGRESS' },
    })).toThrow()
  })

  it('rejects a response with a negative total', () => {
    expect(() => normalizeCommunityStatsResponse({
      players: { total: -1, currentSeasonCount: 0 },
      clubs: { total: 1, currentSeasonCount: 0 },
      matches: { total: 1, currentSeasonCount: 0 },
      season: { name: '2025-2026', status: 'IN_PROGRESS' },
    })).toThrow()
  })

  it('rejects a response missing the season status', () => {
    expect(() => normalizeCommunityStatsResponse({
      players: { total: 1, currentSeasonCount: 0 },
      clubs: { total: 1, currentSeasonCount: 0 },
      matches: { total: 1, currentSeasonCount: 0 },
      season: { name: '2025-2026', status: '' },
    })).toThrow()
  })

  it('requests the versioned community statistics endpoint with the session token', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({
        players: { total: 1, currentSeasonCount: 0 },
        clubs: { total: 1, currentSeasonCount: 0 },
        matches: { total: 1, currentSeasonCount: 0 },
        season: { name: '2025-2026', status: 'IN_PROGRESS' },
      }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await getCommunityStatistics('session-token')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/stats/community',
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: expect.any(String) }) }),
    )
  })

  it('propagates unauthorized responses to the caller', async () => {
    const response = {
      ok: false,
      status: 401,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'Unauthorized' }),
    }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
    const onUnauthorized = vi.fn()

    await expect(getCommunityStatistics('session-token', undefined, onUnauthorized)).rejects.toThrow()
    expect(onUnauthorized).toHaveBeenCalled()
  })
})
