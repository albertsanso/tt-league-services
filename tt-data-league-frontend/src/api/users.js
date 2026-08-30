import { apiRequest, ApiError } from './client.js'

function requireText(value, field) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new ApiError(`La resposta d'usuari no conté ${field}.`, 502, value)
  }
  return value.trim()
}

function requireUuid(value, field) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new ApiError(`La resposta d'usuari no conté ${field}.`, 502, value)
  }
  return value.trim()
}

export function normalizeUser(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta d\'usuari no és vàlida.', 502, value)
  }
  if (!Array.isArray(value.roles)) {
    throw new ApiError('La resposta d\'usuari conté rols no vàlids.', 502, value)
  }
  if (!Array.isArray(value.permissions)) {
    throw new ApiError('La resposta d\'usuari conté permisos no vàlids.', 502, value)
  }
  return {
    id: requireUuid(value.id, 'un identificador'),
    username: requireText(value.username, 'un nom d\'usuari'),
    email: requireText(value.email, 'un correu electrònic'),
    createdAt: value.createdAt ?? null,
    active: Boolean(value.active),
    roles: value.roles.map((r) => requireText(r, 'un rol')).sort(),
    permissions: value.permissions.map((p) => requireText(p, 'un permís')).sort(),
  }
}

export function normalizeUserPage(payload) {
  if (!payload || typeof payload !== 'object') {
    throw new ApiError('La resposta de la llista d\'usuaris no és vàlida.', 502, payload)
  }
  if (!Array.isArray(payload.content)) {
    throw new ApiError('La resposta de la llista d\'usuaris no conté contingut.', 502, payload)
  }
  return {
    content: payload.content.map(normalizeUser),
    totalElements: Number(payload.totalElements ?? 0),
    totalPages: Number(payload.totalPages ?? 1),
    page: Number(payload.page ?? 0),
    size: Number(payload.size ?? 20),
  }
}

export function normalizeRole(value) {
  if (!value || typeof value !== 'object') {
    throw new ApiError('La resposta de rols no és vàlida.', 502, value)
  }
  if (!Array.isArray(value.permissions)) {
    throw new ApiError('La resposta de rols conté permisos no vàlids.', 502, value)
  }
  return {
    name: requireText(value.name, 'un nom de rol'),
    permissions: value.permissions.map((p) => requireText(p, 'un permís')).sort(),
  }
}

export function getUsers({ search, active, page = 0, size = 20 } = {}, token, signal, onUnauthorized) {
  const params = new URLSearchParams()
  if (search) params.set('search', search)
  if (active !== undefined && active !== null) params.set('active', String(active))
  params.set('page', String(page))
  params.set('size', String(size))

  return apiRequest(`/api/v1/user?${params.toString()}`, { token, signal, onUnauthorized })
    .then(normalizeUserPage)
}

export function getUserById(id, token, signal, onUnauthorized) {
  if (!id || typeof id !== 'string') {
    throw new ApiError('L\'identificador de l\'usuari no és vàlid.', 400)
  }
  return apiRequest(`/api/v1/user/${encodeURIComponent(id)}`, { token, signal, onUnauthorized })
    .then(normalizeUser)
}

export function createUser({ username, email, password, roles }, token, signal, onUnauthorized) {
  return apiRequest('/api/v1/user', {
    method: 'POST',
    body: { username, email, password, roles },
    token,
    signal,
    onUnauthorized,
  }).then(normalizeUser)
}

export function updateUser(id, { username, email, roles }, token, signal, onUnauthorized) {
  if (!id || typeof id !== 'string') {
    throw new ApiError('L\'identificador de l\'usuari no és vàlid.', 400)
  }
  return apiRequest(`/api/v1/user/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: { username, email, roles },
    token,
    signal,
    onUnauthorized,
  }).then(normalizeUser)
}

export function setUserActive(id, active, token, signal, onUnauthorized) {
  if (!id || typeof id !== 'string') {
    throw new ApiError('L\'identificador de l\'usuari no és vàlid.', 400)
  }
  return apiRequest(`/api/v1/user/${encodeURIComponent(id)}/active`, {
    method: 'PATCH',
    body: { active: Boolean(active) },
    token,
    signal,
    onUnauthorized,
  })
}

export function getRoles(token, signal, onUnauthorized) {
  return apiRequest('/api/v1/user/roles', { token, signal, onUnauthorized })
    .then((payload) => {
      if (!Array.isArray(payload)) {
        throw new ApiError('La resposta del catàleg de rols no és vàlida.', 502, payload)
      }
      return payload.map(normalizeRole)
    })
}

export function deleteUser(id, token, signal, onUnauthorized) {
  if (!id || typeof id !== 'string') {
    throw new ApiError("L'identificador de l'usuari no és vàlid.", 400)
  }
  const url = '/api/v1/user/' + encodeURIComponent(id)
  return apiRequest(url, {
    method: 'DELETE',
    token,
    signal,
    onUnauthorized,
  })
}
