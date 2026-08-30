import { apiRequest } from './client.js'

const basePath = '/api/v1/administration/settings'

export function normalizeSetting(value) {
  if (!value || typeof value.key !== 'string' || typeof value.category !== 'string'
    || typeof value.type !== 'string' || value.version === undefined) {
    throw new Error('Invalid system setting response')
  }
  return {
    ...value,
    allowedValues: Array.isArray(value.allowedValues) ? value.allowedValues : [],
  }
}

export function getSettings(filters = {}, token, signal, onUnauthorized) {
  const params = new URLSearchParams()
  if (filters.category) params.set('category', filters.category)
  if (filters.search) params.set('search', filters.search)
  const query = params.toString()
  return apiRequest(`${basePath}${query ? `?${query}` : ''}`, { token, signal, onUnauthorized })
}

export function updateSetting(key, value, version, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/${encodeURIComponent(key)}`, {
    method: 'PUT', token, signal, onUnauthorized, body: { value, version },
  })
}

export function previewSettings(changes, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/preview`, {
    method: 'POST', token, signal, onUnauthorized, body: changes,
  })
}

export function updateSettings(changes, versions, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/bulk`, {
    method: 'POST', token, signal, onUnauthorized, body: { changes, versions },
  })
}

export function downloadSettingsBackup(token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/backup`, { token, signal, onUnauthorized })
}

export function restoreSettingsBackup(backup, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/restore`, {
    method: 'POST', token, signal, onUnauthorized, body: backup,
  })
}
