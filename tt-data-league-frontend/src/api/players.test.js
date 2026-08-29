import { beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizePlayerDetailsResponse, searchPlayers } from './players.js'

describe('player API boundary', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('normalizes canonical detail collections', () => {
    const details = normalizePlayerDetailsResponse({
      id: 'player-id',
      name: 'Anna Player',
      federatedPlayers: [{ id: 'federated-id', name: 'Anna RFETM', source: 'RFETM' }],
      registrations: [{ id: 'registration-id', name: 'Anna Player', season: '2025', source: 'RFETM' }],
      clubs: [{ id: 'club-id', name: 'Club Terrassa', season: '2025', source: 'RFETM' }],
      competitions: [{ name: 'Preferent', season: '2025', source: 'RFETM', matchCount: 2 }],
      matches: [{
        id: 'match-id', competition: 'Preferent', season: '2025', source: 'RFETM',
        homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona',
      }],
    })

    expect(details.id).toBe('player-id')
    expect(details.registrations[0].season).toBe('2025')
    expect(details.matches[0].homeTeam).toBe('Club Terrassa')
  })

  it('encodes the source filter and session token', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => [{ id: 'federated-id', name: 'Anna', source: 'RFETM' }],
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await searchPlayers(' Anna ', 'RFETM', 'session-token')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/player/search_in_name?name=Anna&source=RFETM',
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: expect.any(String) }) }),
    )
  })
})
