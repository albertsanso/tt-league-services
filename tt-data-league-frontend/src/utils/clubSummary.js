function sumResultTotals(competitions) {
  return competitions.reduce((totals, competition) => {
    const resultTotals = competition.resultTotals ?? {}
    return {
      wins: totals.wins + Number(resultTotals.wins ?? 0),
      draws: totals.draws + Number(resultTotals.draws ?? 0),
      losses: totals.losses + Number(resultTotals.losses ?? 0),
      matchCount: totals.matchCount + Number(competition.matchCount ?? 0),
    }
  }, { wins: 0, draws: 0, losses: 0, matchCount: 0 })
}

function winRateOf({ wins, draws, losses }) {
  const decided = wins + draws + losses
  return decided === 0 ? 0 : Math.round((wins / decided) * 100)
}

export function computeOverallRecord(competitions) {
  const totals = sumResultTotals(competitions)
  return { ...totals, winRate: winRateOf(totals) }
}

export function computeWinRateBySeason(seasons, competitions) {
  return seasons
    .map((season) => {
      const totals = sumResultTotals(competitions.filter((competition) => competition.season === season))
      return { season, winRate: winRateOf(totals), matchCount: totals.matchCount }
    })
    .filter((point) => point.matchCount > 0)
    .map(({ season, winRate }) => ({ season, winRate }))
}

export function getRecentMatches(matches, limit = 4) {
  return [...matches]
    .sort((left, right) => {
      if (!left.dateTime && !right.dateTime) return 0
      if (!left.dateTime) return 1
      if (!right.dateTime) return -1
      return new Date(right.dateTime).getTime() - new Date(left.dateTime).getTime()
    })
    .slice(0, limit)
}

export function getTopPlayers(players, limit = 4) {
  return [...players]
    .sort((left, right) => {
      const byCompetitionCount = right.competitions.length - left.competitions.length
      if (byCompetitionCount !== 0) return byCompetitionCount
      return (left.playerName ?? left.registrationName).localeCompare(right.playerName ?? right.registrationName)
    })
    .slice(0, limit)
}

export function getTopPerformers(players, limit = 4, minMatches = 5) {
  return players
    .filter((player) => (player.matchCount ?? 0) >= minMatches)
    .map((player) => ({ ...player, winRate: winRateOf(player.resultTotals ?? { wins: 0, draws: 0, losses: 0 }) }))
    .sort((left, right) => {
      if (right.winRate !== left.winRate) return right.winRate - left.winRate
      if (right.matchCount !== left.matchCount) return right.matchCount - left.matchCount
      return (left.playerName ?? left.registrationName).localeCompare(right.playerName ?? right.registrationName)
    })
    .slice(0, limit)
}
