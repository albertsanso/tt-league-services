import { describe, expect, it } from 'vitest'
import { groupMatchesHierarchy, UNKNOWN_TEAM } from './clubMatches.js'

function group(source, season, competition, matches = []) {
  return { source, season, competition, matches }
}

function match(id, homeTeam, awayTeam, round = 1) {
  return { id, homeTeam, awayTeam, round }
}

const teams = [
  { id: 't1', name: 'Club A', source: 'RFETM', season: '2024-2025' },
  { id: 't2', name: 'Club A B', source: 'RFETM', season: '2024-2025' },
  { id: 't3', name: 'Club A', source: 'RFETM', season: '2023-2024' },
]

describe('groupMatchesHierarchy', () => {
  it('buckets flat match groups into source > season > competition, sorted', () => {
    const hierarchy = groupMatchesHierarchy([
      group('RFETM', '2023-2024', 'Preferent', [match('m1', 'Club A', 'Rival')]),
      group('BCNESA', '2024-2025', 'Copa', [match('m2', 'Club A', 'Rival')]),
      group('RFETM', '2024-2025', 'Preferent', [match('m3', 'Club A', 'Rival')]),
      group('RFETM', '2024-2025', 'Copa', [match('m4', 'Club A', 'Rival')]),
    ])

    expect(hierarchy.map((node) => node.source)).toEqual(['BCNESA', 'RFETM'])

    const rfetm = hierarchy.find((node) => node.source === 'RFETM')
    expect(rfetm.seasons.map((node) => node.season)).toEqual(['2024-2025', '2023-2024'])

    const latestSeason = rfetm.seasons.find((node) => node.season === '2024-2025')
    expect(latestSeason.competitions.map((node) => node.competition)).toEqual(['Copa', 'Preferent'])
    expect(latestSeason.competitions[0].teams).toEqual([{ team: UNKNOWN_TEAM, matches: [match('m4', 'Club A', 'Rival')] }])
  })

  it('returns an empty hierarchy for an empty input', () => {
    expect(groupMatchesHierarchy([])).toEqual([])
  })

  describe('Team level', () => {
    it('resolves the club team from the home side', () => {
      const hierarchy = groupMatchesHierarchy([
        group('RFETM', '2024-2025', 'Preferent', [match('m1', 'Club A', 'Rival')]),
      ], { teams })

      const competitionNode = hierarchy[0].seasons[0].competitions[0]
      expect(competitionNode.teams).toEqual([{ team: 'Club A', matches: [match('m1', 'Club A', 'Rival')] }])
    })

    it('resolves the club team from the away side', () => {
      const hierarchy = groupMatchesHierarchy([
        group('RFETM', '2024-2025', 'Preferent', [match('m1', 'Rival', 'Club A B')]),
      ], { teams })

      const competitionNode = hierarchy[0].seasons[0].competitions[0]
      expect(competitionNode.teams).toEqual([{ team: 'Club A B', matches: [match('m1', 'Rival', 'Club A B')] }])
    })

    it('falls back to an unknown-team bucket when neither side matches a known club team', () => {
      const hierarchy = groupMatchesHierarchy([
        group('RFETM', '2024-2025', 'Preferent', [match('m1', 'Rival A', 'Rival B')]),
      ], { teams })

      const competitionNode = hierarchy[0].seasons[0].competitions[0]
      expect(competitionNode.teams).toEqual([{ team: UNKNOWN_TEAM, matches: [match('m1', 'Rival A', 'Rival B')] }])
    })

    it('scopes team resolution by source and season, and sorts teams alphabetically with unknown last', () => {
      const hierarchy = groupMatchesHierarchy([
        group('RFETM', '2024-2025', 'Preferent', [
          match('m1', 'Club A B', 'Rival'),
          match('m2', 'Club A', 'Rival'),
          match('m3', 'Rival A', 'Rival B'),
        ]),
      ], { teams })

      const competitionNode = hierarchy[0].seasons[0].competitions[0]
      expect(competitionNode.teams.map((node) => node.team)).toEqual(['Club A', 'Club A B', UNKNOWN_TEAM])
    })
  })

  describe('level omission', () => {
    const matchGroups = [
      group('RFETM', '2024-2025', 'Preferent', [match('m1', 'Club A', 'Rival')]),
      group('RFETM', '2023-2024', 'Preferent', [match('m2', 'Club A', 'Rival')]),
    ]

    it('omits the season level, merging its competitions directly under source', () => {
      const hierarchy = groupMatchesHierarchy(matchGroups, { teams, omitLevels: new Set(['season']) })

      expect(hierarchy[0]).not.toHaveProperty('seasons')
      expect(hierarchy[0].competitions).toBeDefined()
    })

    it('omits the competition level, merging its teams directly under season', () => {
      const hierarchy = groupMatchesHierarchy(matchGroups, { teams, omitLevels: new Set(['competition']) })

      expect(hierarchy[0].seasons[0]).not.toHaveProperty('competitions')
      expect(hierarchy[0].seasons[0].teams).toBeDefined()
    })

    it('omits both season and competition levels, going straight from source to teams', () => {
      const hierarchy = groupMatchesHierarchy(
        matchGroups,
        { teams, omitLevels: new Set(['season', 'competition']) },
      )

      expect(hierarchy[0]).not.toHaveProperty('seasons')
      expect(hierarchy[0]).not.toHaveProperty('competitions')
      expect(hierarchy[0].teams).toBeDefined()
      expect(hierarchy[0].teams.map((node) => node.team)).toEqual(['Club A'])
      expect(hierarchy[0].teams[0].matches.map((item) => item.id)).toEqual(['m1', 'm2'])
    })

    it('ignores an omitted source level in the data shape (source is always the outermost grouping)', () => {
      const hierarchy = groupMatchesHierarchy(matchGroups, { teams, omitLevels: new Set(['source']) })

      expect(hierarchy).toHaveLength(1)
      expect(hierarchy[0].source).toBe('RFETM')
      expect(hierarchy[0].seasons).toBeDefined()
    })
  })
})
