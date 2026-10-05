import type { MatchResult } from '../api/types'

export type ResultsView =
  | { readonly state: 'loading' }
  | { readonly state: 'unavailable' }
  | { readonly state: 'loaded'; readonly byMatch: ReadonlyMap<string, MatchResult> }

/** `3 – 1` once played, `—` when not played, and a word when the platform could not be read. */
export function resultText(results: ResultsView, matchId: string): string {
  if (results.state === 'loading') {
    return '…'
  }
  if (results.state === 'unavailable') {
    return 'unavailable'
  }
  const result = results.byMatch.get(matchId)
  if (result === undefined || result.homeGamesWon === null || result.awayGamesWon === null) {
    return '—'
  }
  return `${result.homeGamesWon} – ${result.awayGamesWon}`
}
