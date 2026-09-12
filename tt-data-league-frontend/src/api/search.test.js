import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchGlobal } from './search.js'

describe('global search API boundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('encodes the search, sends the session token, and normalizes each group', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({
        players: [{ id: 'player-id', name: 'Anna Player', canonicalPlayerId: 'player-id', federatedPlayers: [] }],
        clubs: [{ id: 'club-id', name: 'Club Terrassa', source: 'RFETM' }],
        matches: [{ id: 'match-id', homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona' }],
      }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    const results = await searchGlobal('  Anna  ', 'session-token')

    expect(results.players).toHaveLength(1)
    expect(results.players[0].id).toBe('player-id')
    expect(results.clubs).toEqual([{ id: 'club-id', name: 'Club Terrassa', source: 'RFETM' }])
    expect(results.matches).toEqual([{ id: 'match-id', homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona' }])
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/search?q=Anna',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
      }),
    )
  })

  it('rejects searches shorter than two characters before requesting', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')

    await expect(searchGlobal('a', 'session-token')).rejects.toMatchObject({ status: 400 })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects a payload missing one of the result groups', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ players: [], clubs: [] }),
    }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await expect(searchGlobal('Anna', 'session-token')).rejects.toMatchObject({ status: 502 })
  })
})
