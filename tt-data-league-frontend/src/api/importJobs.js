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

export function getImportResourcesBySource(token, source, signal, onUnauthorized) {
  const params = new URLSearchParams({ source })
  return apiRequest(`${basePath}/list_by_source?${params}`, { token, signal, onUnauthorized })
}

export function uploadImportFile(token, file, onProgress, onUnauthorized, signal) {
  const formData = new FormData()
  formData.append('file', file)
  return apiRequest(`${basePath}/upload`, {
    token,
    method: 'POST',
    body: formData,
    signal,
    onUnauthorized,
    onUploadProgress: onProgress,
  })
}

export const uploadImport = uploadImportFile

export function createImportPreview(token, importResourceId, onUnauthorized) {
  const params = new URLSearchParams({ importResourceId })
  return apiRequest(`${basePath}/preview?${params}`, { token, method: 'POST', onUnauthorized })
}

export function validateImport(token, id, onUnauthorized) {
  return apiRequest(`${basePath}/${id}/validate`, { token, method: 'POST', onUnauthorized })
}

export function startImport(token, importResourceId, onUnauthorized) {
  const params = new URLSearchParams({ importResourceId })
  return apiRequest(`${basePath}/start?${params}`, { token, method: 'POST', onUnauthorized })
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
