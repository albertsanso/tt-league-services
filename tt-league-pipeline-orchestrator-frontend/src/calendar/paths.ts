export function matchDayPath(id: string): string {
  return `/calendar/match-days/${encodeURIComponent(id)}`
}
