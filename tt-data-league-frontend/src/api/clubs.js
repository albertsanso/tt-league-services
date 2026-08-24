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

  const club = {
    id: requireText(value.id, 'un identificador'),
    name: requireText(value.name, 'un nom'),
    source: value.source == null ? '—' : requireText(value.source, 'la font'),
  }
  if (value.sources != null && !Array.isArray(value.sources)) {
    throw new ApiError('La resposta conté fonts no vàlides.', 502, value)
  }
  if (Array.isArray(value.sources)) {
    club.sources = value.sources.map((source) => requireText(source, 'una font')).sort()
  }
  if (value.federatedClubs != null && !Array.isArray(value.federatedClubs)) {
    throw new ApiError('La resposta conté clubs federats no vàlids.', 502, value)
  }
  if (Array.isArray(value.federatedClubs)) {
    club.federatedClubs = value.federatedClubs.map((federatedClub) => ({
      id: requireText(federatedClub.id, 'un identificador federat'),
      name: requireText(federatedClub.name, 'un nom federat'),
      source: federatedClub.source == null ? '—' : requireText(federatedClub.source, 'la font federada'),
    }))
  }
  if (value.competitions != null && !Array.isArray(value.competitions)) {
    throw new ApiError('La resposta conté competicions no vàlides.', 502, value)
  }
  if (value.competitions != null && value.playerCount !== undefined) {
    club.competitions = value.competitions.map((competition) => ({
      name: requireText(competition.name, 'un nom de competició'),
      source: competition.source == null ? '—' : requireText(competition.source, 'la font de la competició'),
      season: requireText(String(competition.season ?? ''), 'una temporada de competició'),
      matchCount: Number(competition.matchCount ?? 0),
      wins: Number(competition.wins ?? 0),
      draws: Number(competition.draws ?? 0),
      losses: Number(competition.losses ?? 0),
    }))
    club.playerCount = Number(value.playerCount)
    if (!Number.isFinite(club.playerCount) || club.playerCount < 0) {
      throw new ApiError('La resposta conté un recompte de jugadors no vàlid.', 502, value)
    }
  }
  if (value.seasons != null && !Array.isArray(value.seasons)) {
    throw new ApiError('La resposta conté temporades no vàlides.', 502, value)
  }
  if (Array.isArray(value.seasons)) {
    club.seasons = value.seasons.map((season) => requireText(String(season), 'una temporada')).sort()
  }
  return club
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
  const clubs = getCollection(payload, ['clubs', 'results', 'content']).map(normalizeClub)
  const clubsById = new Map()
  clubs.forEach((club) => {
    const previous = clubsById.get(club.id)
    if (!previous) {
      clubsById.set(club.id, club)
      return
    }
    const merged = { ...previous }
    if (club.sources || previous.sources) {
      merged.sources = [...new Set([...(previous.sources ?? []), ...(club.sources ?? [])])].sort()
    }
    if (club.federatedClubs || previous.federatedClubs) {
      const federatedById = new Map(
        [...(previous.federatedClubs ?? []), ...(club.federatedClubs ?? [])]
          .map((federatedClub) => [federatedClub.id, federatedClub]),
      )
      merged.federatedClubs = [...federatedById.values()]
    }
    if (club.competitions || previous.competitions) {
      const competitionsByKey = new Map(
        [...(previous.competitions ?? []), ...(club.competitions ?? [])]
          .map((competition) => [
            `${competition.source}-${competition.season}-${competition.name}`,
            competition,
          ]),
      )
      merged.competitions = [...competitionsByKey.values()]
      merged.playerCount = Math.max(previous.playerCount ?? 0, club.playerCount ?? 0)
    }
    if (club.seasons || previous.seasons) {
      merged.seasons = [...new Set([...(previous.seasons ?? []), ...(club.seasons ?? [])])].sort()
    }
    clubsById.set(club.id, merged)
  })
  return [...clubsById.values()]
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

  const competition = {
    name: requireText(value.name, 'un nom de competició'),
    season: requireText(String(value.season ?? ''), 'una temporada de competició'),
    matchCount,
    resultTotals: normalizeResultTotals(resultTotals),
  }
  if (value.source != null) {
    competition.source = requireText(value.source, 'la font de la competició')
  }
  return competition
}

function normalizePlayer(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta conté un jugador no vàlid.', 502, value)
  }

  const competitions = value.competitions == null ? [] : value.competitions
  if (!Array.isArray(competitions)
    || competitions.some((competition) => typeof competition !== 'string' || !competition.trim())) {
    throw new ApiError('La resposta conté competicions de jugador no vàlides.', 502, value)
  }

  return {
    playerSeasonId: requireText(value.playerSeasonId, 'un identificador de registre'),
    playerId: value.playerId == null ? null : requireText(value.playerId, 'un identificador de jugador'),
    playerName: value.playerName == null ? null : requireText(value.playerName, 'un nom de jugador'),
    canonicalPlayerId: value.canonicalPlayerId == null
      ? null
      : requireText(value.canonicalPlayerId, 'un identificador de jugador canònic'),
    canonicalPlayerName: value.canonicalPlayerName == null
      ? null
      : requireText(value.canonicalPlayerName, 'un nom de jugador canònic'),
    registrationName: requireText(value.registrationName, 'un nom de registre'),
    license: value.license == null ? '—' : requireText(value.license, 'una llicència'),
    source: value.source == null ? '—' : requireText(value.source, 'la font del jugador'),
    season: requireText(String(value.season ?? ''), 'una temporada de jugador'),
    competitions: competitions.map((competition) => competition.trim()),
  }
}

export function normalizeClubDetailsResponse(payload) {
  const club = normalizeClub(payload)
  if (!Array.isArray(payload.teams) || !Array.isArray(payload.competitions)
    || (payload.players != null && !Array.isArray(payload.players))
    || (payload.federatedClubs != null && !Array.isArray(payload.federatedClubs))
    || (payload.sources != null && !Array.isArray(payload.sources))) {
    throw new ApiError('La resposta detallada del club no és vàlida.', 502, payload)
  }

  return {
    ...club,
    teams: payload.teams.map(normalizeTeam),
    competitions: payload.competitions.map(normalizeCompetition),
    players: (payload.players ?? []).map(normalizePlayer),
    federatedClubs: (payload.federatedClubs ?? []).map((federatedClub) => ({
      id: requireText(federatedClub.id, 'un identificador federat'),
      name: requireText(federatedClub.name, 'un nom federat'),
      source: federatedClub.source == null ? '—' : requireText(federatedClub.source, 'la font federada'),
    })),
    sources: Array.isArray(payload.sources)
      ? payload.sources.map((source) => requireText(source, 'una font')).sort()
      : club.sources,
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

function normalizeMatch(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta conté un partit no vàlid.', 502, value)
  }

  return {
    id: requireText(value.id, 'un identificador de partit'),
    homeTeam: requireText(value.homeTeam, 'un equip local'),
    awayTeam: requireText(value.awayTeam, 'un equip visitant'),
    homeGamesWon: value.homeGamesWon == null ? null : Number(value.homeGamesWon),
    awayGamesWon: value.awayGamesWon == null ? null : Number(value.awayGamesWon),
    result: requireText(value.result, 'un resultat'),
    round: Number(value.round ?? 0),
    dateTime: value.dateTime ?? null,
    city: value.city ?? null,
    venue: value.venue ?? null,
  }
}

export function normalizeClubCompetitionDetailsResponse(payload) {
  if (!payload || typeof payload !== 'object' || !Array.isArray(payload.matches)) {
    throw new ApiError('La resposta detallada de la competició no és vàlida.', 502, payload)
  }

  return {
    clubId: requireText(payload.clubId, 'un identificador de club'),
    clubName: requireText(payload.clubName, 'un nom de club'),
    source: requireText(payload.source, 'la font del club'),
    competition: requireText(payload.competition, 'un nom de competició'),
    season: requireText(String(payload.season ?? ''), 'una temporada'),
    matches: payload.matches.map(normalizeMatch),
  }
}

export function getClubCompetitionDetails(
  clubId,
  season,
  competition,
  token,
  signal,
  onUnauthorized,
) {
  if (!clubId || typeof clubId !== 'string' || !season || typeof season !== 'string'
    || !competition || typeof competition !== 'string') {
    throw new ApiError('Els filtres de competició no són vàlids.', 400)
  }

  return apiRequest(
    `/api/v1/club/${encodeURIComponent(clubId)}/competition/${encodeURIComponent(season)}/${encodeURIComponent(competition)}`,
    { token, signal, onUnauthorized },
  ).then(normalizeClubCompetitionDetailsResponse)
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
