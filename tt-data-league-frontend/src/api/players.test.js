import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getPlayerDetails,
  normalizePlayerDetailsResponse,
  normalizePlayerSearchResponse,
  searchPlayers,
} from './players.js'

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

  it('requests player details with every selected filter', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({
        id: 'player-id',
        name: 'Anna Player',
        federatedPlayers: [],
        registrations: [],
        clubs: [],
        competitions: [],
        matches: [],
        statistics: [],
      }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await getPlayerDetails(
      'player-id',
      'RFETM',
      '2024-2025',
      'Preferent',
      'session-token',
      new AbortController().signal,
    )

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/player/player-id?source=RFETM&season=2024-2025&competition=Preferent',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('normalizes one canonical result with source context', () => {
    const players = normalizePlayerSearchResponse([{
      id: 'canonical-id',
      name: 'Anna Canonical',
      canonicalPlayerId: 'canonical-id',
      sources: ['FCTT', 'RFETM'],
      seasons: ['2024-2025', '2023-2024'],
      federatedPlayers: [
        { id: 'fctt-id', name: 'Anna FCTT', license: '1', source: 'FCTT' },
        { id: 'rfetm-id', name: 'Anna RFETM', license: '2', source: 'RFETM' },
      ],
    }])

    expect(players).toHaveLength(1)
    expect(players[0].sources).toEqual(['FCTT', 'RFETM'])
    expect(players[0].seasons).toEqual(['2024-2025', '2023-2024'])
    expect(players[0].federatedPlayers).toHaveLength(2)
  })

  it('rejects malformed source context', () => {
    expect(() => normalizePlayerSearchResponse([{
      id: 'player-id',
      name: 'Anna',
      sources: 'RFETM',
    }])).toThrow()
  })

  it('normalizes source-scoped game opponents and rejects malformed scores', () => {
    const payload = {
      id: 'player-id',
      name: 'Anna Player',
      federatedPlayers: [],
      registrations: [],
      clubs: [],
      competitions: [],
      matches: [{
        id: 'match-id', competition: 'Preferent', season: '2025', source: 'RFETM',
        homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona', playerTeam: 'Club Terrassa',
        result: 'win', homeGamesWon: 4, awayGamesWon: 2,
        games: [{
          id: 'game-id', gameNumber: 1, type: 'INDIVIDUAL', result: 'win',
          homeSetsWon: 3, awaySetsWon: 1,
          opponents: [{
            playerId: 'canonical-opponent', federatedPlayerId: 'federated-opponent',
            playerSeasonId: 'season-opponent', name: 'Opponent Player',
            source: 'RFETM', season: '2025', available: true,
          }],
        }],
      }],
      statistics: [],
    }

    expect(normalizePlayerDetailsResponse(payload).matches[0].games[0].opponents[0].playerId)
      .toBe('canonical-opponent')
    expect(() => normalizePlayerDetailsResponse({
      ...payload,
      matches: [{ ...payload.matches[0], homeGamesWon: '4' }],
    })).toThrow()
  })
})
