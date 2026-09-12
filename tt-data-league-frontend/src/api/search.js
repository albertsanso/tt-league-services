import { apiRequest, ApiError } from './client.js'
import { normalizePlayerSearchResponse } from './players.js'
import { normalizeClubSearchResponse } from './clubs.js'

function normalizeMatch(value) {
  if (!value || typeof value !== 'object' || typeof value.id !== 'string') {
    throw new ApiError('La resposta de partits no és vàlida.', 502, value)
  }
  return value
}

function normalizeQuery(query) {
  if (typeof query !== 'string' || query.trim().length < 2) {
    throw new ApiError('La cerca necessita almenys 2 caràcters.', 400)
  }
  return query.trim()
}

export function searchGlobal(query, token, signal, onUnauthorized) {
  return Promise.resolve().then(() => {
    const normalizedQuery = normalizeQuery(query)
    return apiRequest(
      `/api/v1/search?q=${encodeURIComponent(normalizedQuery)}`,
      { token, signal, onUnauthorized },
    )
  }).then((payload) => {
    if (!payload || typeof payload !== 'object'
      || !Array.isArray(payload.players) || !Array.isArray(payload.clubs) || !Array.isArray(payload.matches)) {
      throw new ApiError('La resposta de la cerca global no és vàlida.', 502, payload)
    }
    return {
      players: normalizePlayerSearchResponse(payload.players),
      clubs: normalizeClubSearchResponse(payload.clubs),
      matches: payload.matches.map(normalizeMatch),
    }
  })
}
