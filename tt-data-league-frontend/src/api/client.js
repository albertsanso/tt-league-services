export class ApiError extends Error {
  constructor(message, status, details = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

async function readResponse(response) {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) return null
  return response.json()
}

function parseResponseBody(body, contentType = '') {
  if (!contentType.includes('application/json') || !body) return null
  try {
    return JSON.parse(body)
  } catch {
    return null
  }
}

function authorizationHeaders(token) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function rejectApiResponse(payload, status, onUnauthorized) {
  if (status === 401) onUnauthorized?.()
  throw new ApiError(
    payload?.message ?? 'No s’ha pogut completar la sol·licitud.',
    status,
    payload,
  )
}

function uploadRequest(path, {
  token,
  method = 'POST',
  body,
  signal,
  onUnauthorized,
  onUploadProgress,
}) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    let settled = false
    let abort

    const finish = (callback, value) => {
      if (settled) return
      settled = true
      if (signal) signal.removeEventListener('abort', abort)
      callback(value)
    }
    abort = () => {
      request.abort()
      finish(reject, new DOMException('The request was aborted', 'AbortError'))
    }

    request.open(method, path)
    Object.entries(authorizationHeaders(token)).forEach(([name, value]) => request.setRequestHeader(name, value))
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) onUploadProgress?.(Math.round((event.loaded / event.total) * 100))
    })
    request.addEventListener('load', () => {
      const contentType = request.getResponseHeader('content-type') ?? ''
      const payload = parseResponseBody(request.responseText, contentType)
      if (request.status >= 200 && request.status < 300) {
        onUploadProgress?.(100)
        finish(resolve, payload)
      } else {
        try {
          rejectApiResponse(payload, request.status, onUnauthorized)
        } catch (error) {
          finish(reject, error)
        }
      }
    })
    request.addEventListener('error', () => finish(reject, new ApiError('No s’ha pogut completar la sol·licitud.', 0)))
    request.addEventListener('abort', () => finish(reject, new DOMException('The request was aborted', 'AbortError')))
    if (signal) {
      if (signal.aborted) {
        abort()
        return
      }
      signal.addEventListener('abort', abort, { once: true })
    }
    request.send(body)
  })
}

export async function apiRequest(path, options = {}) {
  const {
    token,
    signal,
    method = 'GET',
    body,
    onUnauthorized,
    onUploadProgress,
  } = options
  const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData

  if (isMultipart && onUploadProgress) {
    return uploadRequest(path, {
      token,
      method,
      body,
      signal,
      onUnauthorized,
      onUploadProgress,
    })
  }

  const headers = authorizationHeaders(token)
  if (body !== undefined && !isMultipart) headers['Content-Type'] = 'application/json'

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : isMultipart ? body : JSON.stringify(body),
    signal,
  })
  const payload = await readResponse(response)

  if (!response.ok) rejectApiResponse(payload, response.status, onUnauthorized)
  return payload
}
