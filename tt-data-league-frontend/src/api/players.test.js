import { beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizePlayerDetailsResponse, normalizePlayerSearchResponse, searchPlayers } from './players.js'

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
        homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona', playerTeam: 'Club Terrassa', result: 'draw',
      }],
      statistics: [{
        source: 'RFETM', season: '2025-2026', matchesPlayed: 2, wins: 1, losses: 1,
        winPercentage: 50, averageScore: 3.5,
      }],
    })

    expect(details.id).toBe('player-id')
    expect(details.registrations[0].season).toBe('2025')
    expect(details.matches[0].homeTeam).toBe('Club Terrassa')
    expect(details.statistics[0].winPercentage).toBe(50)
  })

  it('rejects a match without a participating player team', () => {
    expect(() => normalizePlayerDetailsResponse({
      id: 'player-id',
      name: 'Anna Player',
      federatedPlayers: [],
      registrations: [],
      clubs: [],
      competitions: [],
      matches: [{
        id: 'match-id', competition: 'Preferent', season: '2025', source: 'RFETM',
        homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona', result: 'draw',
      }],
      statistics: [],
    })).toThrow('equip del jugador')
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

  it('normalizes one canonical result with source context', () => {
    const players = normalizePlayerSearchResponse([{
      id: 'canonical-id',
      name: 'Anna Canonical',
      canonicalPlayerId: 'canonical-id',
      sources: ['FCTT', 'RFETM'],
      federatedPlayers: [
        { id: 'fctt-id', name: 'Anna FCTT', license: '1', source: 'FCTT' },
        { id: 'rfetm-id', name: 'Anna RFETM', license: '2', source: 'RFETM' },
      ],
    }])

    expect(players).toHaveLength(1)
    expect(players[0].sources).toEqual(['FCTT', 'RFETM'])
    expect(players[0].federatedPlayers).toHaveLength(2)
  })

  it('rejects malformed source context', () => {
    expect(() => normalizePlayerSearchResponse([{
      id: 'player-id',
      name: 'Anna',
      sources: 'RFETM',
    }])).toThrow()
  })
})
