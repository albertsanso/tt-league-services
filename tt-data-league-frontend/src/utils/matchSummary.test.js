import { describe, expect, it } from 'vitest'
import { computeHomeAwaySplit, computeRecord } from './matchSummary.js'

function match(overrides) {
  return {
    id: 'm1',
    homeTeam: 'Club Terrassa',
    awayTeam: 'Club Beta',
    playerTeam: 'Club Terrassa',
    result: 'win',
    ...overrides,
  }
}

describe('computeRecord', () => {
  it('aggregates wins, draws, losses, match count and win rate', () => {
    const matches = [
      match({ id: 'm1', result: 'win' }),
      match({ id: 'm2', result: 'win' }),
      match({ id: 'm3', result: 'draw' }),
      match({ id: 'm4', result: 'loss' }),
    ]

    expect(computeRecord(matches)).toEqual({ wins: 2, draws: 1, losses: 1, matchCount: 4, winRate: 50 })
  })

  it('returns a zero win rate when there are no matches', () => {
    expect(computeRecord([])).toEqual({ wins: 0, draws: 0, losses: 0, matchCount: 0, winRate: 0 })
  })
})

describe('computeHomeAwaySplit', () => {
  it('classifies a match as home when the player team is the home team', () => {
    const matches = [match({ homeTeam: 'Club Terrassa', awayTeam: 'Club Beta', playerTeam: 'Club Terrassa', result: 'win' })]

    expect(computeHomeAwaySplit(matches)).toEqual({
      home: { wins: 1, draws: 0, losses: 0, winRate: 100 },
      away: { wins: 0, draws: 0, losses: 0, winRate: 0 },
    })
  })

  it('classifies a match as away when the player team is the away team', () => {
    const matches = [match({ homeTeam: 'Club Beta', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'loss' })]

    expect(computeHomeAwaySplit(matches)).toEqual({
      home: { wins: 0, draws: 0, losses: 0, winRate: 0 },
      away: { wins: 0, draws: 0, losses: 1, winRate: 0 },
    })
  })

  it('excludes a match where the player team matches neither side', () => {
    const matches = [match({ homeTeam: 'Club A', awayTeam: 'Club B', playerTeam: 'Club Terrassa', result: 'win' })]

    expect(computeHomeAwaySplit(matches)).toEqual({
      home: { wins: 0, draws: 0, losses: 0, winRate: 0 },
      away: { wins: 0, draws: 0, losses: 0, winRate: 0 },
    })
  })
})
