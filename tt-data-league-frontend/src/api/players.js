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
  const federatedPlayers = value.federatedPlayers == null ? [] : value.federatedPlayers
  if (!Array.isArray(federatedPlayers)) {
    throw new ApiError('La resposta conté context de jugador no vàlid.', 502, value)
  }
  if (value.sources != null && !Array.isArray(value.sources)) {
    throw new ApiError('La resposta conté fonts de jugador no vàlides.', 502, value)
  }
  return {
    id: text(value.id, 'un identificador'),
    name: text(value.name, 'un nom'),
    source: value.source == null ? '—' : text(value.source, 'la font'),
    canonicalPlayerId: value.canonicalPlayerId == null ? null : text(value.canonicalPlayerId, 'un identificador canònic'),
    canonicalPlayerName: value.canonicalPlayerName == null ? null : text(value.canonicalPlayerName, 'un nom canònic'),
    sources: Array.isArray(value.sources)
      ? value.sources.map((source) => text(source, 'una font')).sort()
      : federatedPlayers.map((item) => text(item.source, 'la font del context')).filter(Boolean).sort(),
    federatedPlayers: federatedPlayers.map((item) => ({
      id: text(item.id, 'un identificador federat'),
      name: text(item.name, 'un nom federat'),
      license: item.license == null ? null : text(item.license, 'una llicència'),
      source: item.source == null ? '—' : text(item.source, 'la font del context'),
    })),
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

function normalizeMatch(item) {
  const homeTeam = text(item.homeTeam, 'un equip local')
  const awayTeam = text(item.awayTeam, 'un equip visitant')
  const playerTeam = text(item.playerTeam, 'l’equip del jugador')
  if (playerTeam !== homeTeam && playerTeam !== awayTeam) {
    throw new ApiError('La resposta no associa el jugador a cap equip del partit.', 502, item)
  }
  if (!['win', 'loss', 'draw'].includes(item.result)) {
    throw new ApiError('La resposta no conté un resultat de partit vàlid.', 502, item)
  }
  return {
    ...item,
    id: text(item.id, 'un identificador de partit'),
    season: item.season == null ? '—' : text(String(item.season), 'una temporada de partit'),
    source: item.source == null ? '—' : text(item.source, 'la font del partit'),
    competition: item.competition == null ? '—' : text(item.competition, 'una competició'),
    homeTeam,
    awayTeam,
    homeGamesWon: item.homeGamesWon == null ? null : integer(item.homeGamesWon, 'els jocs locals guanyats'),
    awayGamesWon: item.awayGamesWon == null ? null : integer(item.awayGamesWon, 'els jocs visitants guanyats'),
    playerTeam,
    playerGamesWon: item.playerGamesWon == null ? null : integer(item.playerGamesWon, 'els jocs guanyats'),
    games: item.games == null ? [] : normalizeGames(item.games),
  }
}

function normalizeGames(value) {
  if (!Array.isArray(value)) {
    throw new ApiError('La resposta conté jocs no vàlids.', 502, value)
  }
  return value.map((game) => {
    if (!game || typeof game !== 'object') {
      throw new ApiError('La resposta conté un joc no vàlid.', 502, game)
    }
    const opponents = game.opponents == null ? [] : game.opponents
    if (!Array.isArray(opponents)) {
      throw new ApiError('La resposta conté oponents no vàlids.', 502, game)
    }
    return {
      id: text(game.id, 'un identificador de joc'),
      gameNumber: integer(game.gameNumber, 'el número de joc'),
      type: text(game.type, 'el tipus de joc'),
      result: normalizeGameResult(game.result, game),
      homeSetsWon: game.homeSetsWon == null ? null : integer(game.homeSetsWon, 'els sets locals'),
      awaySetsWon: game.awaySetsWon == null ? null : integer(game.awaySetsWon, 'els sets visitants'),
      unavailableReason: game.unavailableReason == null ? null : text(game.unavailableReason, 'el motiu de dades no disponibles'),
      opponents: opponents.map(normalizeOpponent),
    }
  })
}

function normalizeOpponent(item) {
  if (!item || typeof item !== 'object' || typeof item.available !== 'boolean') {
    throw new ApiError('La resposta conté un oponent no vàlid.', 502, item)
  }
  const playerId = item.playerId == null ? null : text(item.playerId, 'un identificador canònic d’oponent')
  const federatedPlayerId = item.federatedPlayerId == null ? null : text(item.federatedPlayerId, 'un identificador federat d’oponent')
  const playerSeasonId = item.playerSeasonId == null ? null : text(item.playerSeasonId, 'un identificador de temporada d’oponent')
  const name = item.name == null ? null : text(item.name, 'un nom d’oponent')
  if (item.available && (!playerId && !federatedPlayerId && !playerSeasonId || !name)) {
    throw new ApiError('La resposta conté un oponent disponible sense identitat.', 502, item)
  }
  return {
    playerId,
    federatedPlayerId,
    playerSeasonId,
    name,
    source: item.source == null ? null : text(item.source, 'la font de l’oponent'),
    season: item.season == null ? null : text(String(item.season), 'la temporada de l’oponent'),
    available: item.available,
  }
}

function normalizeGameResult(value, item) {
  if (!['win', 'loss', 'draw', 'unavailable'].includes(value)) {
    throw new ApiError('La resposta conté un resultat de joc no vàlid.', 502, item)
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
    matches: normalizeArray(payload.matches, 'partits').map(normalizeMatch),
    statistics: normalizeArray(payload.statistics, 'estadístiques').map((item) => ({
      source: item.source == null ? null : text(item.source, 'la font de les estadístiques'),
      season: item.season == null ? null : text(String(item.season), 'la temporada de les estadístiques'),
      matchesPlayed: integer(item.matchesPlayed, 'els partits jugats'),
      wins: integer(item.wins, 'les victòries'),
      losses: integer(item.losses, 'les derrotes'),
      winPercentage: nullableNumber(item.winPercentage, 'el percentatge de victòries', 0, 100),
      averageScore: nullableNumber(item.averageScore, 'la puntuació mitjana', 0),
    })),
  }
  return details
}

function integer(value, field) {
  if (!Number.isInteger(value) || value < 0) {
    throw new ApiError(`La resposta no conté ${field} vàlids.`, 502, value)
  }
  return value
}

function nullableNumber(value, field, min = Number.NEGATIVE_INFINITY, max = Number.POSITIVE_INFINITY) {
  if (value == null) return null
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
    throw new ApiError(`La resposta no conté ${field} vàlid.`, 502, value)
  }
  return value
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

export function getPlayerDetails(playerId, source, season, competition, token, signal, onUnauthorized) {
  if (!playerId || typeof playerId !== 'string') {
    throw new ApiError('L’identificador del jugador no és vàlid.', 400)
  }
  if (arguments.length <= 4) {
    onUnauthorized = competition
    signal = season
    token = source
    source = null
    season = null
    competition = null
  }
  const query = [
    ['source', source],
    ['season', season],
    ['competition', competition],
  ]
    .filter(([, value]) => value != null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&')
  return apiRequest(`/api/v1/player/${encodeURIComponent(playerId)}${query ? `?${query}` : ''}`, {
    token, signal, onUnauthorized,
  }).then(normalizePlayerDetailsResponse)
}
