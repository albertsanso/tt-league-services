import { apiRequest } from './client.js'

const basePath = '/api/v1/administration/settings'

export function normalizeSetting(value) {
  if (!value || typeof value.name !== 'string' || typeof value.category !== 'string') {
    throw new Error('Invalid system setting response')
  }
  return {
    ...value,
    type: value.type ?? inferType(value.value),
    version: value.version ?? 0,
    allowedValues: Array.isArray(value.allowedValues) ? value.allowedValues : [],
  }
}

function inferType(value) {
  if (value === 'true' || value === 'false') return 'BOOLEAN'
  if (value !== '' && Number.isInteger(Number(value))) return 'INTEGER'
  return 'STRING'
}

export function getSettings(filters = {}, token, signal, onUnauthorized) {
  const params = new URLSearchParams()
  if (filters.category) params.set('category', filters.category)
  if (filters.search) params.set('search', filters.search)
  const query = params.toString()
  return apiRequest(`${basePath}${query ? `?${query}` : ''}`, { token, signal, onUnauthorized })
}

export function updateSetting(settingId, value, version, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/${encodeURIComponent(settingId)}`, {
    method: 'PUT', token, signal, onUnauthorized, body: { value, version },
  })
}

export function createSetting(setting, token, signal, onUnauthorized) {
  return apiRequest(basePath, {
    method: 'POST', token, signal, onUnauthorized,
    body: { category: setting.category, name: setting.name, value: setting.value },
  })
}

export function deleteSetting(settingId, token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/${encodeURIComponent(settingId)}`, {
    method: 'DELETE', token, signal, onUnauthorized,
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
