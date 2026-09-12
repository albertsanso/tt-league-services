import { describe, expect, it } from 'vitest'
import {
  computeOverallRecord,
  computeWinRateBySeason,
  getRecentMatches,
  getTopPerformers,
  getTopPlayers,
} from './clubSummary.js'

function competition(season, name, { wins = 0, draws = 0, losses = 0, matchCount } = {}) {
  return {
    name,
    season,
    matchCount: matchCount ?? wins + draws + losses,
    resultTotals: { wins, draws, losses },
  }
}

function player(name, competitions) {
  return { playerName: name, registrationName: name, competitions }
}

describe('computeOverallRecord', () => {
  it('aggregates wins, draws, losses and win rate across competitions', () => {
    const record = computeOverallRecord([
      competition('2024-2025', 'Primera', { wins: 9, draws: 1, losses: 2 }),
      competition('2024-2025', 'Segona', { wins: 2, draws: 0, losses: 4 }),
    ])

    expect(record).toEqual({ wins: 11, draws: 1, losses: 6, matchCount: 18, winRate: 61 })
  })

  it('returns zero win rate when there are no matches yet', () => {
    expect(computeOverallRecord([])).toEqual({ wins: 0, draws: 0, losses: 0, matchCount: 0, winRate: 0 })
  })
})

describe('computeWinRateBySeason', () => {
  it('returns an ordered win-rate point per season with data', () => {
    const competitions = [
      competition('2023-2024', 'Primera', { wins: 5, draws: 0, losses: 5 }),
      competition('2024-2025', 'Primera', { wins: 8, draws: 0, losses: 2 }),
    ]

    expect(computeWinRateBySeason(['2023-2024', '2024-2025'], competitions)).toEqual([
      { season: '2023-2024', winRate: 50 },
      { season: '2024-2025', winRate: 80 },
    ])
  })

  it('skips seasons without any recorded matches', () => {
    const competitions = [competition('2024-2025', 'Primera', { wins: 1, draws: 0, losses: 0 })]

    expect(computeWinRateBySeason(['2023-2024', '2024-2025'], competitions)).toEqual([
      { season: '2024-2025', winRate: 100 },
    ])
  })

  it('returns an empty array for a club with a single season and no competitions', () => {
    expect(computeWinRateBySeason(['2024-2025'], [])).toEqual([])
  })
})

describe('getRecentMatches', () => {
  it('sorts matches by date descending and limits the result', () => {
    const matches = [
      { id: 'm1', dateTime: '2024-03-01T00:00:00Z' },
      { id: 'm2', dateTime: '2024-03-08T00:00:00Z' },
      { id: 'm3', dateTime: '2024-02-22T00:00:00Z' },
      { id: 'm4', dateTime: '2024-03-15T00:00:00Z' },
      { id: 'm5', dateTime: '2024-02-15T00:00:00Z' },
    ]

    expect(getRecentMatches(matches, 4).map((match) => match.id)).toEqual(['m4', 'm2', 'm1', 'm3'])
  })

  it('places matches without a date at the end', () => {
    const matches = [
      { id: 'm1', dateTime: null },
      { id: 'm2', dateTime: '2024-03-08T00:00:00Z' },
    ]

    expect(getRecentMatches(matches).map((match) => match.id)).toEqual(['m2', 'm1'])
  })

  it('returns an empty array when there are no matches yet', () => {
    expect(getRecentMatches([])).toEqual([])
  })
})

describe('getTopPlayers', () => {
  it('ranks players by number of competitions played', () => {
    const players = [
      player('Anna', ['Divisió Honor']),
      player('Marc', ['Primera', 'Copa']),
      player('Laia', ['Primera']),
      player('Jordi', ['Segona', 'Copa']),
    ]

    expect(getTopPlayers(players, 3).map((item) => item.playerName)).toEqual(['Jordi', 'Marc', 'Anna'])
  })

  it('breaks ties alphabetically by name', () => {
    const players = [player('Bernat', ['Primera']), player('Anna', ['Primera'])]

    expect(getTopPlayers(players).map((item) => item.playerName)).toEqual(['Anna', 'Bernat'])
  })

  it('returns an empty array when the club has no players', () => {
    expect(getTopPlayers([])).toEqual([])
  })
})

function performer(name, { wins = 0, draws = 0, losses = 0, matchCount } = {}) {
  return {
    playerName: name,
    registrationName: name,
    matchCount: matchCount ?? wins + draws + losses,
    resultTotals: { wins, draws, losses },
  }
}

describe('getTopPerformers', () => {
  it('ranks eligible players by win rate', () => {
    const players = [
      performer('Anna', { wins: 9, draws: 0, losses: 1 }),
      performer('Marc', { wins: 5, draws: 0, losses: 5 }),
      performer('Laia', { wins: 8, draws: 0, losses: 2 }),
    ]

    expect(getTopPerformers(players).map((item) => item.playerName)).toEqual(['Anna', 'Laia', 'Marc'])
  })

  it('excludes players below the minimum-matches floor', () => {
    const players = [
      performer('Anna', { wins: 1, draws: 0, losses: 0 }),
      performer('Marc', { wins: 30, draws: 0, losses: 5 }),
    ]

    expect(getTopPerformers(players).map((item) => item.playerName)).toEqual(['Marc'])
  })

  it('breaks a win-rate tie by matches played, then alphabetically', () => {
    const players = [
      performer('Bernat', { wins: 5, draws: 0, losses: 0 }),
      performer('Anna', { wins: 5, draws: 0, losses: 0 }),
      performer('Clara', { wins: 10, draws: 0, losses: 0 }),
    ]

    expect(getTopPerformers(players).map((item) => item.playerName)).toEqual(['Clara', 'Anna', 'Bernat'])
  })

  it('attaches the computed win rate to each returned player', () => {
    const players = [performer('Anna', { wins: 8, draws: 0, losses: 2 })]

    expect(getTopPerformers(players)[0].winRate).toBe(80)
  })

  it('respects the limit and custom minMatches', () => {
    const players = [
      performer('Anna', { wins: 3, draws: 0, losses: 0 }),
      performer('Marc', { wins: 3, draws: 0, losses: 0 }),
    ]

    expect(getTopPerformers(players, 1, 3).map((item) => item.playerName)).toEqual(['Anna'])
  })

  it('returns an empty array when no player meets the floor', () => {
    expect(getTopPerformers([performer('Anna', { wins: 1, draws: 0, losses: 0 })])).toEqual([])
  })
})
