import { resolveClubTeam } from './clubMatches.js'

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

function sumCompetitionResults(results) {
  return results.reduce((totals, item) => ({
    wins: totals.wins + Number(item.resultTotals?.wins ?? 0),
    draws: totals.draws + Number(item.resultTotals?.draws ?? 0),
    losses: totals.losses + Number(item.resultTotals?.losses ?? 0),
    matchCount: totals.matchCount + Number(item.matchCount ?? 0),
  }), { wins: 0, draws: 0, losses: 0, matchCount: 0 })
}

export function scopePlayerResultsToCompetition(players, competition) {
  return players.map((player) => {
    const competitionResults = player.competitionResults ?? []
    if (competitionResults.length === 0) {
      return player
    }
    const relevant = competition
      ? competitionResults.filter((item) => item.competition === competition)
      : competitionResults
    const { matchCount, ...resultTotals } = sumCompetitionResults(relevant)
    return { ...player, matchCount, resultTotals }
  })
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

export function aggregateRosterByCanonicalPlayer(players) {
  const byId = new Map()
  players.forEach((player) => {
    if (!player.canonicalPlayerId) return
    const resultTotals = player.resultTotals ?? { wins: 0, draws: 0, losses: 0 }
    const existing = byId.get(player.canonicalPlayerId)
    if (!existing) {
      byId.set(player.canonicalPlayerId, {
        canonicalPlayerId: player.canonicalPlayerId,
        playerName: player.playerName ?? player.registrationName,
        source: player.source,
        competitions: new Set(player.competitions),
        seasons: new Set([player.season]),
        matchCount: Number(player.matchCount ?? 0),
        resultTotals: {
          wins: Number(resultTotals.wins ?? 0),
          draws: Number(resultTotals.draws ?? 0),
          losses: Number(resultTotals.losses ?? 0),
        },
      })
      return
    }
    player.competitions.forEach((competition) => existing.competitions.add(competition))
    existing.seasons.add(player.season)
    existing.matchCount += Number(player.matchCount ?? 0)
    existing.resultTotals.wins += Number(resultTotals.wins ?? 0)
    existing.resultTotals.draws += Number(resultTotals.draws ?? 0)
    existing.resultTotals.losses += Number(resultTotals.losses ?? 0)
  })

  return [...byId.values()].map((entry) => ({
    ...entry,
    competitions: [...entry.competitions],
    seasons: [...entry.seasons].sort(),
  }))
}

export function getRosterByCompetition(rosterAggregates) {
  const counts = new Map()
  rosterAggregates.forEach((player) => {
    player.competitions.forEach((competition) => {
      counts.set(competition, (counts.get(competition) ?? 0) + 1)
    })
  })

  return [...counts.entries()]
    .map(([competition, playerCount]) => ({ competition, playerCount }))
    .sort((left, right) => {
      if (right.playerCount !== left.playerCount) return right.playerCount - left.playerCount
      return left.competition.localeCompare(right.competition)
    })
}

export function getMostActivePlayers(rosterAggregates, limit = 4) {
  return [...rosterAggregates]
    .sort((left, right) => {
      if (right.matchCount !== left.matchCount) return right.matchCount - left.matchCount
      if (right.seasons.length !== left.seasons.length) return right.seasons.length - left.seasons.length
      return left.playerName.localeCompare(right.playerName)
    })
    .slice(0, limit)
}

// "Latest season on record" — there is no join/registration-date field on a
// player-season record, only `season` (e.g. '2023-2024'), so this is an
// approximation of "newest addition" rather than a true join date.
export function getLatestSeasonPlayer(rosterAggregates) {
  return [...rosterAggregates]
    .sort((left, right) => {
      const leftLatest = left.seasons[left.seasons.length - 1] ?? ''
      const rightLatest = right.seasons[right.seasons.length - 1] ?? ''
      if (rightLatest !== leftLatest) return rightLatest.localeCompare(leftLatest)
      return left.playerName.localeCompare(right.playerName)
    })[0] ?? null
}

function isPendingMatch(match) {
  return match.homeGamesWon == null || match.awayGamesWon == null
}

export function countPendingMatches(matches) {
  return matches.filter(isPendingMatch).length
}

export function computeHomeAwaySplit(matches, teams, source) {
  const totals = { home: { wins: 0, draws: 0, losses: 0 }, away: { wins: 0, draws: 0, losses: 0 } }
  matches.forEach((match) => {
    const clubTeam = resolveClubTeam(match, teams, match.source ?? source, match.season)
    if (!clubTeam) return
    const side = clubTeam === match.homeTeam ? 'home' : clubTeam === match.awayTeam ? 'away' : null
    if (!side) return
    if (match.result === 'win') totals[side].wins += 1
    else if (match.result === 'draw') totals[side].draws += 1
    else if (match.result === 'loss') totals[side].losses += 1
  })

  return {
    home: { ...totals.home, winRate: winRateOf(totals.home) },
    away: { ...totals.away, winRate: winRateOf(totals.away) },
  }
}

export function getFormGuide(matches, limit = 5) {
  return [...getRecentMatches(matches, limit)].reverse()
}

export function getCurrentStreak(matches) {
  const chronological = getRecentMatches(matches, matches.length)
  if (chronological.length === 0) return null

  const [latest, ...rest] = chronological
  let count = 1
  for (const match of rest) {
    if (match.result !== latest.result) break
    count += 1
  }
  return { result: latest.result, count }
}

export function getNotableMatches(matches) {
  const decided = matches.filter((match) => !isPendingMatch(match))
  const withMargin = decided.map((match) => ({
    match,
    margin: Math.abs(match.homeGamesWon - match.awayGamesWon),
  }))

  function pick(candidates, compare) {
    if (candidates.length === 0) return null
    return candidates.reduce((best, current) => (compare(current, best) < 0 ? current : best)).match
  }

  const byRecency = (left, right) => (
    new Date(right.match.dateTime ?? 0).getTime() - new Date(left.match.dateTime ?? 0).getTime()
  )

  const biggestWin = pick(
    withMargin.filter(({ match }) => match.result === 'win'),
    (left, right) => (right.margin !== left.margin ? right.margin - left.margin : byRecency(left, right)),
  )
  const biggestLoss = pick(
    withMargin.filter(({ match }) => match.result === 'loss'),
    (left, right) => (right.margin !== left.margin ? right.margin - left.margin : byRecency(left, right)),
  )
  // Excludes whatever was already picked as biggestWin/biggestLoss so the
  // strip never renders the exact same match under two different labels.
  const closest = pick(
    withMargin.filter(({ match }) => match.id !== biggestWin?.id && match.id !== biggestLoss?.id),
    (left, right) => (
      left.margin !== right.margin ? left.margin - right.margin : byRecency(left, right)
    ),
  )

  return { closest, biggestWin, biggestLoss }
}
