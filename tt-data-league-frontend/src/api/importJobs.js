import { apiRequest } from './client.js'

const basePath = '/api/v1/administration/import'

/**
 * Returns the list of supported import sources for the current deployment.
 * @returns {Promise<Array<{id: string, label: string}>>}
 */
export function getImportSources(token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/sources`, { token, signal, onUnauthorized })
}

export function getImportStatus(token, signal, onUnauthorized) {
  return apiRequest(`${basePath}/status`, { token, signal, onUnauthorized })
}

export function createImportPreview(token, request, onUnauthorized) {
  return apiRequest(`${basePath}/preview`, { token, method: 'POST', body: request, onUnauthorized })
}

export function validateImport(token, id, onUnauthorized) {
  return apiRequest(`${basePath}/${id}/validate`, { token, method: 'POST', onUnauthorized })
}

export function startImport(token, id, onUnauthorized) {
  return apiRequest(`${basePath}/${id}/start`, { token, method: 'POST', onUnauthorized })
}

export function cancelImport(token, id, onUnauthorized) {
  return apiRequest(`${basePath}/${id}/cancel`, { token, method: 'POST', onUnauthorized })
}

export function rollbackImport(token, id, onUnauthorized) {
  return apiRequest(`${basePath}/${id}/rollback`, { token, method: 'POST', onUnauthorized })
}

export function getImportHistory(token, query = '', onUnauthorized, signal) {
  const params = new URLSearchParams({ query, limit: '100' })
  return apiRequest(`${basePath}?${params}`, { token, signal, onUnauthorized })
}
