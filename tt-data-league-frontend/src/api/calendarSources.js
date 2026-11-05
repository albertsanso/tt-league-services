import { getCalendarRange, getMatchOptions } from './matches.js'

export const CALENDAR_SOURCES = ['RFETM', 'FCTT', 'BCNESA']

function listOf(value) {
  return Array.isArray(value) ? value : []
}

function unique(values) {
  return [...new Set(values)]
}

function compareDescending(a, b) {
  if (a === b) return 0
  return a < b ? 1 : -1
}

function compareAscending(a, b) {
  if (a === b) return 0
  return a < b ? -1 : 1
}

function compareGroups(a, b) {
  if (a == null || b == null) return a == null && b == null ? 0 : a == null ? 1 : -1
  return a - b
}

function compareMatches(a, b) {
  const first = a.dateTime ?? ''
  const second = b.dateTime ?? ''
  if (first !== second) {
    if (!first) return 1
    if (!second) return -1
    return compareAscending(first, second)
  }
  return compareAscending(String(a.id), String(b.id))
}

function compareTeams(a, b) {
  const byName = compareAscending(String(a.name ?? ''), String(b.name ?? ''))
  return byName !== 0 ? byName : compareAscending(String(a.teamId ?? ''), String(b.teamId ?? ''))
}

// Options for one source, or merged across every source when no source is given.
// With no source the seasons are the newest-first union and the competitions an ascending union.
export function getCalendarOptions(source, season, token, signal, onUnauthorized) {
  if (source) {
    return getMatchOptions(source, season, token, signal, onUnauthorized).then((value) => ({
      seasons: listOf(value.seasons),
      competitions: listOf(value.competitions),
    }))
  }
  return Promise.all(CALENDAR_SOURCES.map((federation) => (
    getMatchOptions(federation, season, token, signal, onUnauthorized)
  ))).then((values) => ({
    seasons: unique(values.flatMap((value) => listOf(value.seasons))).sort(compareDescending),
    competitions: unique(values.flatMap((value) => listOf(value.competitions))).sort(compareAscending),
  }))
}

// One calendar range per source, merged into a single range-shaped response. Any failing source
// rejects the whole call so that a partial calendar is never shown as complete.
export function getCalendarRangeAcrossSources(filters, token, signal, onUnauthorized) {
  return Promise.all(CALENDAR_SOURCES.map((federation) => (
    getCalendarRange({ ...filters, source: federation }, token, signal, onUnauthorized)
  ))).then((responses) => {
    const [first] = responses
    const matches = responses.flatMap((response, index) => {
      const source = CALENDAR_SOURCES[index]
      return response.matches.map((match) => ({ ...match, source }))
    }).sort(compareMatches)
    const teams = responses.flatMap((response, index) => {
      const source = CALENDAR_SOURCES[index]
      return response.facets.teams.map((team) => ({ ...team, source }))
    }).sort(compareTeams)
    return {
      ...first,
      source: '',
      matches,
      facets: {
        competitions: unique(responses.flatMap((response) => response.facets.competitions))
          .sort(compareAscending),
        groups: unique(responses.flatMap((response) => response.facets.groups)).sort(compareGroups),
        teams,
      },
    }
  })
}
