import { apiRequest, ApiError } from './client.js'

function text(value, field) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new ApiError(`La resposta del jugador no conté ${field}.`, 502, value)
  }
  return value.trim()
}

function normalizePlayer(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta conté un jugador no vàlid.', 502, value)
  }
  return {
    id: text(value.id, 'un identificador'),
    name: text(value.name, 'un nom'),
    source: value.source == null ? '—' : text(value.source, 'la font'),
    canonicalPlayerId: value.canonicalPlayerId ?? null,
    canonicalPlayerName: value.canonicalPlayerName ?? null,
  }
}

export function normalizePlayerSearchResponse(payload) {
  const values = Array.isArray(payload) ? payload : payload?.players ?? payload?.results
  if (!Array.isArray(values)) {
    throw new ApiError('La resposta de jugadors no és vàlida.', 502, payload)
  }
  return values.map(normalizePlayer)
}

function normalizeArray(value, field) {
  if (!Array.isArray(value)) {
    throw new ApiError(`La resposta no conté ${field} vàlids.`, 502, value)
  }
  return value
}

export function normalizePlayerDetailsResponse(payload) {
  if (!payload || typeof payload !== 'object') {
    throw new ApiError('La resposta detallada del jugador no és vàlida.', 502, payload)
  }
  const details = {
    id: text(payload.id, 'un identificador'),
    name: text(payload.name, 'un nom'),
    federatedPlayers: normalizeArray(payload.federatedPlayers ?? payload.federated, 'registres federats')
      .map((item) => ({ ...item, id: text(item.id, 'un identificador federat') })),
    registrations: normalizeArray(payload.registrations, 'registres de temporada').map((item) => ({
      ...item,
      id: text(item.id, 'un identificador de registre'),
      season: item.season == null ? '—' : text(String(item.season), 'una temporada'),
      source: item.source == null ? '—' : text(item.source, 'la font del registre'),
    })),
    clubs: normalizeArray(payload.clubs, 'clubs').map((item) => ({
      ...item,
      id: text(item.id, 'un identificador de club'),
      name: text(item.name, 'un nom de club'),
      season: item.season == null ? '—' : text(String(item.season), 'una temporada de club'),
      source: item.source == null ? '—' : text(item.source, 'la font del club'),
    })),
    competitions: normalizeArray(payload.competitions, 'competicions').map((item) => ({
      ...item,
      name: text(item.name, 'un nom de competició'),
      season: item.season == null ? '—' : text(String(item.season), 'una temporada'),
      source: item.source == null ? '—' : text(item.source, 'la font de la competició'),
      matchCount: Number(item.matchCount ?? 0),
    })),
    matches: normalizeArray(payload.matches, 'partits').map((item) => ({
      ...item,
      id: text(item.id, 'un identificador de partit'),
      season: item.season == null ? '—' : text(String(item.season), 'una temporada de partit'),
      source: item.source == null ? '—' : text(item.source, 'la font del partit'),
      competition: item.competition ?? '—',
      homeTeam: text(item.homeTeam, 'un equip local'),
      awayTeam: text(item.awayTeam, 'un equip visitant'),
    })),
  }
  return details
}

function normalizeQuery(query) {
  if (typeof query !== 'string' || query.trim().length < 2) {
    throw new ApiError('La cerca necessita almenys 2 caràcters.', 400)
  }
  return query.trim()
}

export function searchPlayers(query, source, token, signal, onUnauthorized) {
  const normalizedQuery = normalizeQuery(query)
  const sourceQuery = source ? `&source=${encodeURIComponent(source)}` : ''
  return apiRequest(
    `/api/v1/player/search_in_name?name=${encodeURIComponent(normalizedQuery)}${sourceQuery}`,
    { token, signal, onUnauthorized },
  ).then(normalizePlayerSearchResponse)
}

export function getPlayerDetails(playerId, token, signal, onUnauthorized) {
  if (!playerId || typeof playerId !== 'string') {
    throw new ApiError('L’identificador del jugador no és vàlid.', 400)
  }
  return apiRequest(`/api/v1/player/${encodeURIComponent(playerId)}`, {
    token, signal, onUnauthorized,
  }).then(normalizePlayerDetailsResponse)
}
