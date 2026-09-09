export const UNKNOWN_TEAM = null

function groupBy(items, keyFn) {
  const map = new Map()
  items.forEach((item) => {
    const key = keyFn(item)
    if (!map.has(key)) {
      map.set(key, [])
    }
    map.get(key).push(item)
  })
  return map
}

function resolveClubTeam(match, teams, source, season) {
  const candidates = teams.filter((team) => team.source === source && team.season === season)
  const homeMatch = candidates.find((team) => team.name === match.homeTeam)
  const awayMatch = candidates.find((team) => team.name === match.awayTeam)
  return (homeMatch ?? awayMatch)?.name ?? UNKNOWN_TEAM
}

function compareTeamNames(left, right) {
  if (left === UNKNOWN_TEAM && right === UNKNOWN_TEAM) return 0
  if (left === UNKNOWN_TEAM) return 1
  if (right === UNKNOWN_TEAM) return -1
  return left.localeCompare(right)
}

function buildTeams(groups, teams) {
  const taggedMatches = groups.flatMap((group) => group.matches.map((match) => ({
    match,
    source: group.source,
    season: group.season,
  })))
  const byTeam = groupBy(taggedMatches, ({ match, source, season }) => (
    resolveClubTeam(match, teams, source, season) ?? UNKNOWN_TEAM
  ))

  return [...byTeam.entries()]
    .sort(([left], [right]) => compareTeamNames(left, right))
    .map(([team, tagged]) => ({
      team,
      matches: tagged.map(({ match }) => match).sort((left, right) => left.round - right.round),
    }))
}

function buildCompetitionsLevel(groups, teams, omitCompetition) {
  if (omitCompetition) {
    return { teams: buildTeams(groups, teams) }
  }

  const byCompetition = groupBy(groups, (group) => group.competition)
  return {
    competitions: [...byCompetition.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([competition, competitionGroups]) => ({
        competition,
        teams: buildTeams(competitionGroups, teams),
      })),
  }
}

export function groupMatchesHierarchy(matchGroups, { teams = [], omitLevels = new Set() } = {}) {
  const omitSeason = omitLevels.has('season')
  const omitCompetition = omitLevels.has('competition')

  const bySource = groupBy(matchGroups, (group) => group.source)

  return [...bySource.entries()]
    .sort(([leftSource], [rightSource]) => leftSource.localeCompare(rightSource))
    .map(([source, sourceGroups]) => {
      if (omitSeason) {
        return { source, ...buildCompetitionsLevel(sourceGroups, teams, omitCompetition) }
      }

      const bySeason = groupBy(sourceGroups, (group) => group.season)
      return {
        source,
        seasons: [...bySeason.entries()]
          .sort(([leftSeason], [rightSeason]) => rightSeason.localeCompare(leftSeason))
          .map(([season, seasonGroups]) => ({
            season,
            ...buildCompetitionsLevel(seasonGroups, teams, omitCompetition),
          })),
      }
    })
}
