import { apiRequest, ApiError } from './client.js'

function requireNonNegativeNumber(value, field) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric < 0) {
    throw new ApiError(`La resposta d'estadístiques conté ${field} no vàlid.`, 502, value)
  }
  return numeric
}

function normalizeCount(value, field) {
  if (!value || typeof value !== 'object') {
    throw new ApiError(`La resposta d'estadístiques no conté ${field}.`, 502, value)
  }
  return {
    total: requireNonNegativeNumber(value.total, `un total de ${field}`),
  }
}

function normalizeSeason(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta d\'estadístiques no conté la temporada.', 502, value)
  }
  if (value.name != null && typeof value.name !== 'string') {
    throw new ApiError('La resposta d\'estadístiques conté una temporada no vàlida.', 502, value)
  }
  if (typeof value.status !== 'string' || !value.status.trim()) {
    throw new ApiError('La resposta d\'estadístiques conté un estat de temporada no vàlid.', 502, value)
  }
  return { name: value.name ?? null, status: value.status }
}

export function normalizeCommunityStatsResponse(payload) {
  if (!payload || typeof payload !== 'object') {
    throw new ApiError('La resposta d\'estadístiques no és vàlida.', 502, payload)
  }
  return {
    players: normalizeCount(payload.players, 'jugadors'),
    clubs: normalizeCount(payload.clubs, 'clubs'),
    matches: normalizeCount(payload.matches, 'partits'),
    season: normalizeSeason(payload.season),
  }
}

export function getCommunityStatistics(token, signal, onUnauthorized) {
  return apiRequest('/api/v1/stats/community', { token, signal, onUnauthorized })
    .then(normalizeCommunityStatsResponse)
}
