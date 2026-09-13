export function formatFormStrip(results) {
  return (results ?? []).map((result) => resultLetter(result?.result))
}

function resultLetter(result) {
  if (result === 'win') return 'W'
  if (result === 'loss') return 'L'
  if (result === 'draw') return 'D'
  return '—'
}

export function formatRecord(results) {
  const values = results ?? []
  return {
    wins: values.filter((result) => result.result === 'win').length,
    draws: values.filter((result) => result.result === 'draw').length,
    losses: values.filter((result) => result.result === 'loss').length,
  }
}

export function computeRecord(matches) {
  const { wins, draws, losses } = formatRecord(matches)
  const decided = wins + draws + losses
  const winRate = decided === 0 ? 0 : Math.round((wins / decided) * 100)
  return { wins, draws, losses, matchCount: matches.length, winRate }
}

function resolvePlayerSide(match) {
  if (match.playerTeam === match.homeTeam) return 'home'
  if (match.playerTeam === match.awayTeam) return 'away'
  return null
}

export function computeHomeAwaySplit(matches) {
  const totals = { home: { wins: 0, draws: 0, losses: 0 }, away: { wins: 0, draws: 0, losses: 0 } }
  matches.forEach((match) => {
    const side = resolvePlayerSide(match)
    if (!side) return
    if (match.result === 'win') totals[side].wins += 1
    else if (match.result === 'draw') totals[side].draws += 1
    else if (match.result === 'loss') totals[side].losses += 1
  })

  const winRateOf = ({ wins, draws, losses }) => {
    const decided = wins + draws + losses
    return decided === 0 ? 0 : Math.round((wins / decided) * 100)
  }

  return {
    home: { ...totals.home, winRate: winRateOf(totals.home) },
    away: { ...totals.away, winRate: winRateOf(totals.away) },
  }
}

export function computeTrendNote(currentWinRate, previousWinRate) {
  if (currentWinRate == null || previousWinRate == null) return null
  if (currentWinRate > previousWinRate) return { tone: 'improved', currentWinRate, previousWinRate }
  if (currentWinRate < previousWinRate) return { tone: 'declining', currentWinRate, previousWinRate }
  return { tone: 'stable', currentWinRate, previousWinRate }
}

export function computeAlignmentBadge(timesFielded) {
  if (timesFielded >= 3) return 'regular'
  if (timesFielded === 2) return 'rare'
  return 'new'
}

export function computeComparisonNote(groupWinRate, teamOverallWinRate) {
  if (groupWinRate == null || teamOverallWinRate == null) return null
  if (groupWinRate > teamOverallWinRate) return 'above'
  if (groupWinRate < teamOverallWinRate) return 'below'
  return 'inline'
}
