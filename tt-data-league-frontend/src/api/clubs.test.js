import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  normalizeClubDetailsResponse,
  normalizeClubSearchResponse,
  searchClubs,
} from './clubs.js'

describe('club API boundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('normalizes club details and flat competition totals', () => {
    const details = normalizeClubDetailsResponse({
      id: 'club-id',
      name: 'Club Terrassa',
      source: 'RFETM',
      teams: [{ id: 'team-id', name: 'Sènior', source: 'RFETM', season: '2025' }],
      competitions: [{
        name: 'Preferent',
        season: '2025',
        matchCount: 8,
        wins: 5,
        draws: 1,
        losses: 2,
      }],
    })

    expect(details.teams[0].season).toBe('2025')
    expect(details.competitions[0]).toEqual({
      name: 'Preferent',
      season: '2025',
      matchCount: 8,
      resultTotals: { wins: 5, draws: 1, losses: 2 },
    })
  })

  it('encodes the search, sends the session token, and normalizes results', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => [{ id: 'club-id', name: 'Club A', source: 'RFETM' }],
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    const clubs = await searchClubs('  Club A  ', 'session-token')

    expect(clubs).toEqual([{ id: 'club-id', name: 'Club A', source: 'RFETM' }])
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/club/search_in_name?name=Club%20A',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
      }),
    )
  })

  it('rejects searches shorter than two characters before requesting', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')

    await expect(searchClubs('a', 'session-token')).rejects.toMatchObject({ status: 400 })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects malformed result payloads instead of returning mock data', () => {
    expect(() => normalizeClubSearchResponse({ results: [{ name: 'Missing id' }] }))
      .toThrow('La resposta del club no conté un identificador')
  })
})
