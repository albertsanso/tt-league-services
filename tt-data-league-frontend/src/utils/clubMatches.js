export function groupMatchesHierarchy(matchGroups) {
  const bySource = new Map()

  matchGroups.forEach((group) => {
    if (!bySource.has(group.source)) {
      bySource.set(group.source, new Map())
    }
    const bySeason = bySource.get(group.source)
    if (!bySeason.has(group.season)) {
      bySeason.set(group.season, [])
    }
    bySeason.get(group.season).push(group)
  })

  return [...bySource.entries()]
    .sort(([leftSource], [rightSource]) => leftSource.localeCompare(rightSource))
    .map(([source, bySeason]) => ({
      source,
      seasons: [...bySeason.entries()]
        .sort(([leftSeason], [rightSeason]) => rightSeason.localeCompare(leftSeason))
        .map(([season, competitions]) => ({
          season,
          competitions: [...competitions].sort(
            (left, right) => left.competition.localeCompare(right.competition),
          ),
        })),
    }))
}
