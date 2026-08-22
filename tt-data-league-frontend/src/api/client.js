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
  if (!contentType.includes('application/json')) {
    return null
  }

  return response.json()
}

export async function apiRequest(path, options = {}) {
  const {
    token,
    signal,
    method = 'GET',
    body,
    onUnauthorized,
  } = options
  const headers = {
    Accept: 'application/json',
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })
  const payload = await readResponse(response)

  if (!response.ok) {
    if (response.status === 401) {
      onUnauthorized?.()
    }
    throw new ApiError(
      payload?.message ?? 'No s’ha pogut completar la sol·licitud.',
      response.status,
      payload,
    )
  }

  return payload
}
