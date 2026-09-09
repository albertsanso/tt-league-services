import { describe, expect, it } from 'vitest'
import { groupMatchesHierarchy } from './clubMatches.js'

function group(source, season, competition, matchIds = []) {
  return {
    source,
    season,
    competition,
    matches: matchIds.map((id) => ({ id })),
  }
}

describe('groupMatchesHierarchy', () => {
  it('buckets flat match groups into source > season > competition, sorted', () => {
    const hierarchy = groupMatchesHierarchy([
      group('RFETM', '2023-2024', 'Preferent', ['m1']),
      group('BCNESA', '2024-2025', 'Copa', ['m2']),
      group('RFETM', '2024-2025', 'Preferent', ['m3']),
      group('RFETM', '2024-2025', 'Copa', ['m4']),
    ])

    expect(hierarchy.map((node) => node.source)).toEqual(['BCNESA', 'RFETM'])

    const rfetm = hierarchy.find((node) => node.source === 'RFETM')
    expect(rfetm.seasons.map((node) => node.season)).toEqual(['2024-2025', '2023-2024'])

    const latestSeason = rfetm.seasons.find((node) => node.season === '2024-2025')
    expect(latestSeason.competitions.map((node) => node.competition)).toEqual(['Copa', 'Preferent'])
    expect(latestSeason.competitions[0].matches).toEqual([{ id: 'm4' }])
  })

  it('returns an empty hierarchy for an empty input', () => {
    expect(groupMatchesHierarchy([])).toEqual([])
  })
})
