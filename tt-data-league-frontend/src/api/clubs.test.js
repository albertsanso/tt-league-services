import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  normalizeClubDetailsResponse,
  normalizeClubCompetitionDetailsResponse,
  getClubCompetitionDetails,
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
      players: [{
        playerSeasonId: 'player-season-id',
        playerId: 'player-id',
        playerName: 'Maria Player',
        canonicalPlayerId: 'canonical-player-id',
        canonicalPlayerName: 'Maria Canonical Player',
        registrationName: 'Maria Player',
        license: '123',
        source: 'RFETM',
        season: '2025',
        competitions: ['Preferent'],
      }],
    })

    expect(details.teams[0].season).toBe('2025')
    expect(details.competitions[0]).toEqual({
      name: 'Preferent',
      season: '2025',
      matchCount: 8,
      resultTotals: { wins: 5, draws: 1, losses: 2 },
    })
    expect(details.players[0].competitions).toEqual(['Preferent'])
    expect(details.players[0].canonicalPlayerId).toBe('canonical-player-id')
    expect(details.players[0].canonicalPlayerName).toBe('Maria Canonical Player')
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

  it('deduplicates canonical results while retaining source context', () => {
    const clubs = normalizeClubSearchResponse({
      results: [
        {
          id: 'canonical-id',
          name: 'Club A',
          source: 'RFETM',
          sources: ['RFETM'],
          federatedClubs: [{ id: 'rfetm-id', name: 'Club A', source: 'RFETM' }],
        },
        {
          id: 'canonical-id',
          name: 'Club A',
          source: 'BCNESA',
          sources: ['BCNESA'],
          federatedClubs: [{ id: 'bcnesa-id', name: 'Club A', source: 'BCNESA' }],
        },
      ],
    })

    expect(clubs).toHaveLength(1)
    expect(clubs[0].sources).toEqual(['BCNESA', 'RFETM'])
    expect(clubs[0].federatedClubs).toHaveLength(2)
  })

  it('normalizes competition details and encodes scoped filters', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({
        clubId: 'club-id',
        clubName: 'Club A',
        source: 'RFETM',
        competition: 'Divisió d’Honor',
        season: '2023-2024',
        matches: [{
          id: 'match-id',
          homeTeam: 'Club A 1',
          awayTeam: 'Club B 1',
          homeGamesWon: 3,
          awayGamesWon: 1,
          result: 'win',
          round: 2,
        }],
      }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
    const details = await getClubCompetitionDetails(
      'club-id',
      '2023-2024',
      'Divisió d’Honor',
      'session-token',
    )

    expect(details.matches[0].result).toBe('win')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/club/club-id/competition/2023-2024/Divisi%C3%B3%20d%E2%80%99Honor',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: expect.any(String) }),
      }),
    )
  })

  it('rejects malformed competition payloads', () => {
    expect(() => normalizeClubCompetitionDetailsResponse({
      clubId: 'club-id',
      clubName: 'Club A',
      source: 'RFETM',
      competition: 'Preferent',
      season: '2023-2024',
      matches: null,
    }))
      .toThrow('La resposta detallada de la competició no és vàlida')
  })
})
