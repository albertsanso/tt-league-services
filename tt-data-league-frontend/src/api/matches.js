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
    competition: required(filters.competition, 'competition'),
    page: String(filters.page ?? 0),
    pageSize: '10',
  })
  const optional = [['fromDate', filters.fromDate], ['toDate', filters.toDate], ['playerId', filters.playerId],
    ['playerLocation', filters.playerLocation], ['playerName', filters.playerName]]
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
