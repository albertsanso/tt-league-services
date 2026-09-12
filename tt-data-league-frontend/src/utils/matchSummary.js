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
