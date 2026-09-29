import { apiRequest, ApiError } from './client.js'

function required(value, field) {
  if (typeof value !== 'string' || !value.trim()) throw new ApiError(`${field} is required`, 400)
  return value.trim()
}

function normalizeMatch(value) {
  if (!value || typeof value !== 'object' || typeof value.id !== 'string') {
    throw new ApiError('La resposta de partits no és vàlida.', 502, value)
  }
  return value
}

export function getMatchOptions(source, season, token, signal, onUnauthorized) {
  const params = new URLSearchParams({ source: required(source, 'source') })
  if (season) params.set('season', season)
  return apiRequest(`/api/v1/match/options?${params}`, { token, signal, onUnauthorized })
}

export function searchMatches(filters, token, signal, onUnauthorized) {
  const params = new URLSearchParams({
    source: required(filters.source, 'source'),
    season: required(filters.season, 'season'),
    page: String(filters.page ?? 0),
    pageSize: '10',
  })
  const optional = [['competition', filters.competition], ['fromDate', filters.fromDate], ['toDate', filters.toDate],
    ['playerId', filters.playerId], ['playerLocation', filters.playerLocation], ['playerName', filters.playerName],
    ['clubName', filters.clubName]]
  optional.forEach(([key, value]) => {
    if (value) params.set(key, value)
  })
  return apiRequest(`/api/v1/match/search?${params}`, { token, signal, onUnauthorized })
    .then((value) => {
      if (!value || !Array.isArray(value.matches)) throw new ApiError('La resposta de partits no és vàlida.', 502, value)
      return { ...value, matches: value.matches.map(normalizeMatch) }
    })
}

export function getMatchDetails(id, token, signal, onUnauthorized) {
  if (!id) throw new ApiError('L’identificador del partit no és vàlid.', 400)
  return apiRequest(`/api/v1/match/${encodeURIComponent(id)}`, { token, signal, onUnauthorized })
}

function normalizeCalendar(value) {
  const valid = value && typeof value === 'object' && Array.isArray(value.groups)
    && value.groups.every((group) => group && typeof group === 'object' && Array.isArray(group.rounds)
      && group.rounds.every((round) => round && typeof round === 'object' && Array.isArray(round.matches)))
  if (!valid) throw new ApiError('La resposta del calendari no és vàlida.', 502, value)
  return value
}

export function getSeasonCalendar(filters, token, signal, onUnauthorized) {
  const params = new URLSearchParams({
    source: required(filters.source, 'source'),
    season: required(filters.season, 'season'),
    competition: required(filters.competition, 'competition'),
  })
  if (filters.group) params.set('group', filters.group)
  if (filters.round) params.set('round', filters.round)
  return apiRequest(`/api/v1/match/calendar?${params}`, { token, signal, onUnauthorized })
    .then(normalizeCalendar)
}

export function markMatchOverdue(id, token, signal, onUnauthorized) {
  if (!id) throw new ApiError('L’identificador del partit no és vàlid.', 400)
  return apiRequest(`/api/v1/match/${encodeURIComponent(id)}/overdue-mark`, {
    method: 'PUT', token, signal, onUnauthorized,
  })
}

export function clearMatchOverdueMark(id, token, signal, onUnauthorized) {
  if (!id) throw new ApiError('L’identificador del partit no és vàlid.', 400)
  return apiRequest(`/api/v1/match/${encodeURIComponent(id)}/overdue-mark`, {
    method: 'DELETE', token, signal, onUnauthorized,
  })
}
