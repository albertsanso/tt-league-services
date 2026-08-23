import { apiRequest, ApiError } from './client.js'

function requireText(value, field) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new ApiError(`La resposta del club no conté ${field}.`, 502, value)
  }

  return value.trim()
}

function normalizeClub(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta del club no és vàlida.', 502, value)
  }

  return {
    id: requireText(value.id, 'un identificador'),
    name: requireText(value.name, 'un nom'),
    source: requireText(value.source, 'la font'),
  }
}

function getCollection(payload, fields) {
  if (Array.isArray(payload)) {
    return payload
  }

  if (!payload || typeof payload !== 'object') {
    throw new ApiError('La resposta de clubs no és vàlida.', 502, payload)
  }

  const collection = fields.find((field) => Array.isArray(payload[field]))
  if (!collection) {
    throw new ApiError('La resposta de clubs no és vàlida.', 502, payload)
  }

  return payload[collection]
}

export function normalizeClubSearchResponse(payload) {
  return getCollection(payload, ['clubs', 'results', 'content']).map(normalizeClub)
}

function normalizeTeam(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta conté un equip no vàlid.', 502, value)
  }

  return {
    id: requireText(value.id, 'un identificador d’equip'),
    name: requireText(value.name, 'un nom d’equip'),
    source: value.source == null ? '—' : requireText(value.source, 'la font de l’equip'),
    season: value.season == null ? '—' : requireText(String(value.season), 'una temporada'),
  }
}

function normalizeResultTotals(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return { ...value }
  }

  if (value === undefined || value === null) {
    return {}
  }

  throw new ApiError('La resposta conté totals de resultats no vàlids.', 502, value)
}

function normalizeCompetition(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta conté una competició no vàlida.', 502, value)
  }

  const rawMatchCount = value.matchCount
    ?? value.availableMatchCount
    ?? value.matchesCount
    ?? value.matchesAvailable
  const matchCount = rawMatchCount === undefined ? 0 : Number(rawMatchCount)
  if (!Number.isFinite(matchCount) || matchCount < 0) {
    throw new ApiError('La resposta conté un recompte de partits no vàlid.', 502, value)
  }
  const resultTotals = value.resultTotals
    ?? value.results
    ?? {
      wins: value.wins ?? 0,
      draws: value.draws ?? 0,
      losses: value.losses ?? 0,
    }

  return {
    name: requireText(value.name, 'un nom de competició'),
    season: requireText(String(value.season ?? ''), 'una temporada de competició'),
    matchCount,
    resultTotals: normalizeResultTotals(resultTotals),
  }
}

export function normalizeClubDetailsResponse(payload) {
  const club = normalizeClub(payload)
  if (!Array.isArray(payload.teams) || !Array.isArray(payload.competitions)) {
    throw new ApiError('La resposta detallada del club no és vàlida.', 502, payload)
  }

  return {
    ...club,
    teams: payload.teams.map(normalizeTeam),
    competitions: payload.competitions.map(normalizeCompetition),
  }
}

function normalizeQuery(query) {
  if (typeof query !== 'string') {
    throw new ApiError('La cerca no és vàlida.', 400, query)
  }

  const value = query.trim()
  if (value.length < 2) {
    throw new ApiError('La cerca necessita almenys 2 caràcters.', 400)
  }

  return value
}

export function searchClubs(query, token, signal, onUnauthorized) {
  return Promise.resolve().then(() => {
    const normalizedQuery = normalizeQuery(query)
    return apiRequest(
      `/api/v1/club/search_in_name?name=${encodeURIComponent(normalizedQuery)}`,
      { token, signal, onUnauthorized },
    )
  }).then(normalizeClubSearchResponse)
}

export function getClubDetails(clubId, token, signal, onUnauthorized) {
  if (!clubId || typeof clubId !== 'string') {
    throw new ApiError('L’identificador del club no és vàlid.', 400)
  }

  return apiRequest(`/api/v1/club/${encodeURIComponent(clubId)}`, {
    token,
    signal,
    onUnauthorized,
  }).then(normalizeClubDetailsResponse)
}

export function updateClubName(clubId, name, token, signal, onUnauthorized) {
  if (!clubId || typeof clubId !== 'string') {
    throw new ApiError('L’identificador del club no és vàlid.', 400)
  }
  if (typeof name !== 'string') {
    throw new ApiError('El nom del club no és vàlid.', 400, name)
  }

  const normalizedName = name.trim()
  if (normalizedName.length < 2) {
    throw new ApiError('El nom del club necessita almenys 2 caràcters.', 400)
  }
  if (normalizedName.length > 255) {
    throw new ApiError('El nom del club no pot superar els 255 caràcters.', 400)
  }

  return apiRequest(`/api/v1/club/${encodeURIComponent(clubId)}`, {
    method: 'PUT',
    body: { name: normalizedName },
    token,
    signal,
    onUnauthorized,
  }).then((payload) => (payload ? normalizeClub(payload) : null))
}
