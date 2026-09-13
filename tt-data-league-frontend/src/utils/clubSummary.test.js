import { describe, expect, it } from 'vitest'
import {
  aggregateRosterByCanonicalPlayer,
  computeHomeAwaySplit,
  computeOverallRecord,
  computeWinRateBySeason,
  countPendingMatches,
  getCurrentStreak,
  getFormGuide,
  getLatestSeasonPlayer,
  getMostActivePlayers,
  getNotableMatches,
  getRecentMatches,
  getRosterByCompetition,
  getTopPerformers,
  getTopPlayers,
  scopePlayerResultsToCompetition,
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

describe('scopePlayerResultsToCompetition', () => {
  function playerWithCompetitionResults(name, competitionResults) {
    return {
      playerName: name,
      registrationName: name,
      matchCount: 999,
      resultTotals: { wins: 999, draws: 999, losses: 999 },
      competitionResults,
    }
  }

  it('uses only the selected competition\'s totals when a competition filter is active', () => {
    const players = [playerWithCompetitionResults('Anna', [
      { competition: 'Preferent', matchCount: 5, resultTotals: { wins: 4, draws: 0, losses: 1 } },
      { competition: 'Copa', matchCount: 2, resultTotals: { wins: 0, draws: 0, losses: 2 } },
    ])]

    const [anna] = scopePlayerResultsToCompetition(players, 'Copa')

    expect(anna.matchCount).toBe(2)
    expect(anna.resultTotals).toEqual({ wins: 0, draws: 0, losses: 2 })
  })

  it('sums every competition when no competition filter is active', () => {
    const players = [playerWithCompetitionResults('Anna', [
      { competition: 'Preferent', matchCount: 5, resultTotals: { wins: 4, draws: 0, losses: 1 } },
      { competition: 'Copa', matchCount: 2, resultTotals: { wins: 0, draws: 0, losses: 2 } },
    ])]

    const [anna] = scopePlayerResultsToCompetition(players, '')

    expect(anna.matchCount).toBe(7)
    expect(anna.resultTotals).toEqual({ wins: 4, draws: 0, losses: 3 })
  })

  it('leaves players without a competition breakdown untouched', () => {
    const players = [{
      playerName: 'Marc',
      registrationName: 'Marc',
      matchCount: 3,
      resultTotals: { wins: 2, draws: 0, losses: 1 },
    }]

    expect(scopePlayerResultsToCompetition(players, 'Copa')).toEqual(players)
  })
})

function rosterPlayer(overrides) {
  return {
    canonicalPlayerId: 'p1',
    playerName: 'Anna',
    registrationName: 'Anna',
    source: 'RFETM',
    season: '2024-2025',
    competitions: ['Preferent'],
    matchCount: 0,
    resultTotals: { wins: 0, draws: 0, losses: 0 },
    ...overrides,
  }
}

describe('aggregateRosterByCanonicalPlayer', () => {
  it('sums match counts and results across a canonical player\'s season records', () => {
    const players = [
      rosterPlayer({ season: '2023-2024', matchCount: 10, resultTotals: { wins: 6, draws: 0, losses: 4 } }),
      rosterPlayer({ season: '2024-2025', matchCount: 8, resultTotals: { wins: 5, draws: 1, losses: 2 } }),
    ]

    const [anna] = aggregateRosterByCanonicalPlayer(players)

    expect(anna.matchCount).toBe(18)
    expect(anna.resultTotals).toEqual({ wins: 11, draws: 1, losses: 6 })
    expect(anna.seasons).toEqual(['2023-2024', '2024-2025'])
  })

  it('unions competitions across season records without duplicates', () => {
    const players = [
      rosterPlayer({ season: '2023-2024', competitions: ['Preferent', 'Copa'] }),
      rosterPlayer({ season: '2024-2025', competitions: ['Preferent'] }),
    ]

    const [anna] = aggregateRosterByCanonicalPlayer(players)

    expect(anna.competitions.sort()).toEqual(['Copa', 'Preferent'])
  })

  it('drops season records without a canonicalPlayerId', () => {
    const players = [rosterPlayer({ canonicalPlayerId: null })]

    expect(aggregateRosterByCanonicalPlayer(players)).toEqual([])
  })
})

describe('getRosterByCompetition', () => {
  it('counts each canonical player once per competition they are part of', () => {
    const aggregates = [
      { ...rosterPlayer({}), competitions: ['Preferent', 'Copa'] },
      { ...rosterPlayer({ canonicalPlayerId: 'p2' }), competitions: ['Preferent'] },
    ]

    expect(getRosterByCompetition(aggregates)).toEqual([
      { competition: 'Preferent', playerCount: 2 },
      { competition: 'Copa', playerCount: 1 },
    ])
  })

  it('breaks a count tie alphabetically by competition name', () => {
    const aggregates = [
      { ...rosterPlayer({}), competitions: ['Segona'] },
      { ...rosterPlayer({ canonicalPlayerId: 'p2' }), competitions: ['Primera'] },
    ]

    expect(getRosterByCompetition(aggregates).map((item) => item.competition)).toEqual(['Primera', 'Segona'])
  })
})

describe('getMostActivePlayers', () => {
  it('ranks by career match count, then seasons on record, then name', () => {
    const aggregates = [
      rosterPlayer({ canonicalPlayerId: 'p1', playerName: 'Anna', matchCount: 5, seasons: ['2024-2025'] }),
      rosterPlayer({ canonicalPlayerId: 'p2', playerName: 'Marc', matchCount: 20, seasons: ['2024-2025'] }),
    ]

    expect(getMostActivePlayers(aggregates).map((item) => item.playerName)).toEqual(['Marc', 'Anna'])
  })

  it('respects the limit', () => {
    const aggregates = [
      rosterPlayer({ canonicalPlayerId: 'p1', matchCount: 1, seasons: ['2024-2025'] }),
      rosterPlayer({ canonicalPlayerId: 'p2', matchCount: 2, seasons: ['2024-2025'] }),
    ]

    expect(getMostActivePlayers(aggregates, 1)).toHaveLength(1)
  })
})

describe('getLatestSeasonPlayer', () => {
  it('picks the player whose most recent season is the latest', () => {
    const aggregates = [
      rosterPlayer({ canonicalPlayerId: 'p1', playerName: 'Anna', seasons: ['2022-2023', '2023-2024'] }),
      rosterPlayer({ canonicalPlayerId: 'p2', playerName: 'Marc', seasons: ['2024-2025'] }),
    ]

    expect(getLatestSeasonPlayer(aggregates).playerName).toBe('Marc')
  })

  it('returns null for an empty roster', () => {
    expect(getLatestSeasonPlayer([])).toBeNull()
  })
})

function match(overrides) {
  return {
    id: 'm1',
    homeTeam: 'Sènior',
    awayTeam: 'Rival TT',
    homeGamesWon: 3,
    awayGamesWon: 1,
    result: 'win',
    round: 1,
    dateTime: '2024-03-01T00:00:00Z',
    source: 'RFETM',
    season: '2024-2025',
    ...overrides,
  }
}

describe('countPendingMatches', () => {
  it('counts matches without a recorded score', () => {
    const matches = [match({ id: 'm1' }), match({ id: 'm2', homeGamesWon: null, awayGamesWon: null })]

    expect(countPendingMatches(matches)).toBe(1)
  })
})

describe('computeHomeAwaySplit', () => {
  const teams = [{ name: 'Sènior', source: 'RFETM', season: '2024-2025' }]

  it('classifies a match as home when the club team is the home team', () => {
    const matches = [match({ homeTeam: 'Sènior', awayTeam: 'Rival TT', result: 'win' })]

    const split = computeHomeAwaySplit(matches, teams)

    expect(split.home).toEqual({ wins: 1, draws: 0, losses: 0, winRate: 100 })
    expect(split.away).toEqual({ wins: 0, draws: 0, losses: 0, winRate: 0 })
  })

  it('classifies a match as away when the club team is the away team', () => {
    const matches = [match({ homeTeam: 'Rival TT', awayTeam: 'Sènior', result: 'loss' })]

    const split = computeHomeAwaySplit(matches, teams)

    expect(split.away).toEqual({ wins: 0, draws: 0, losses: 1, winRate: 0 })
    expect(split.home).toEqual({ wins: 0, draws: 0, losses: 0, winRate: 0 })
  })

  it('ignores matches where the club team cannot be resolved', () => {
    const matches = [match({ homeTeam: 'Unknown A', awayTeam: 'Unknown B' })]

    expect(computeHomeAwaySplit(matches, teams)).toEqual({
      home: { wins: 0, draws: 0, losses: 0, winRate: 0 },
      away: { wins: 0, draws: 0, losses: 0, winRate: 0 },
    })
  })
})

describe('getFormGuide', () => {
  it('returns the most recent matches in chronological order (oldest to newest)', () => {
    const matches = [
      match({ id: 'm1', dateTime: '2024-03-01T00:00:00Z' }),
      match({ id: 'm2', dateTime: '2024-03-08T00:00:00Z' }),
      match({ id: 'm3', dateTime: '2024-03-15T00:00:00Z' }),
    ]

    expect(getFormGuide(matches, 2).map((item) => item.id)).toEqual(['m2', 'm3'])
  })
})

describe('getCurrentStreak', () => {
  it('counts the consecutive most-recent matches sharing the same result', () => {
    const matches = [
      match({ id: 'm1', dateTime: '2024-03-01T00:00:00Z', result: 'loss' }),
      match({ id: 'm2', dateTime: '2024-03-08T00:00:00Z', result: 'win' }),
      match({ id: 'm3', dateTime: '2024-03-15T00:00:00Z', result: 'win' }),
    ]

    expect(getCurrentStreak(matches)).toEqual({ result: 'win', count: 2 })
  })

  it('returns a streak of 1 when the two most recent matches differ', () => {
    const matches = [
      match({ id: 'm1', dateTime: '2024-03-01T00:00:00Z', result: 'win' }),
      match({ id: 'm2', dateTime: '2024-03-08T00:00:00Z', result: 'loss' }),
    ]

    expect(getCurrentStreak(matches)).toEqual({ result: 'loss', count: 1 })
  })

  it('returns null when there are no matches', () => {
    expect(getCurrentStreak([])).toBeNull()
  })
})

describe('getNotableMatches', () => {
  it('picks the biggest win, biggest defeat, and closest result', () => {
    const matches = [
      match({ id: 'blowout-win', result: 'win', homeGamesWon: 5, awayGamesWon: 0 }),
      match({ id: 'blowout-loss', result: 'loss', homeGamesWon: 0, awayGamesWon: 5 }),
      match({ id: 'nail-biter', result: 'win', homeGamesWon: 3, awayGamesWon: 2 }),
    ]

    const notable = getNotableMatches(matches)

    expect(notable.biggestWin.id).toBe('blowout-win')
    expect(notable.biggestLoss.id).toBe('blowout-loss')
    expect(notable.closest.id).toBe('nail-biter')
  })

  it('ignores pending matches without a recorded score', () => {
    const matches = [match({ id: 'pending', homeGamesWon: null, awayGamesWon: null })]

    expect(getNotableMatches(matches)).toEqual({ closest: null, biggestWin: null, biggestLoss: null })
  })

  it('never picks the same match for two different slots', () => {
    const matches = [
      match({ id: 'only-win', result: 'win', homeGamesWon: 3, awayGamesWon: 1 }),
    ]

    const notable = getNotableMatches(matches)

    expect(notable.biggestWin.id).toBe('only-win')
    expect(notable.biggestLoss).toBeNull()
    expect(notable.closest).toBeNull()
  })
})
