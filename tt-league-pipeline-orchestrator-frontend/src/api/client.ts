import { ApiError, problemMessage } from './ApiError'
import type { Problem } from './types'

export type QueryValue = string | number | boolean | null | undefined | readonly (string | number | boolean)[]
export type Query = Readonly<Record<string, QueryValue>>

export interface RequestOptions {
  readonly query?: Query
  readonly body?: unknown
  readonly signal?: AbortSignal
}

export interface HttpResponse<T> {
  readonly status: number
  readonly data: T
}

export interface HttpClient {
  request<T>(method: string, path: string, options?: RequestOptions): Promise<T>
  requestWithStatus<T>(method: string, path: string, options?: RequestOptions): Promise<HttpResponse<T>>
}

export interface HttpClientOptions {
  readonly baseUrl: string
  readonly getToken: () => string | null | undefined
  readonly onUnauthorized?: () => void
  readonly fetch?: typeof fetch
}

export function buildQueryString(query: Query | undefined): string {
  if (!query) {
    return ''
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value]
    for (const item of values) {
      if (item === undefined || item === null || item === '') {
        continue
      }
      params.append(key, String(item))
    }
  }
  const text = params.toString()
  return text === '' ? '' : `?${text}`
}

function isJsonContentType(contentType: string | null): boolean {
  return contentType !== null && /application\/(problem\+)?json/i.test(contentType)
}

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const doFetch = options.fetch ?? ((input, init) => fetch(input, init))

  async function requestWithStatus<T>(
    method: string,
    path: string,
    requestOptions: RequestOptions = {},
  ): Promise<HttpResponse<T>> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = options.getToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    const init: RequestInit = { method, headers, signal: requestOptions.signal }
    if (requestOptions.body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(requestOptions.body)
    }
    const url = `${options.baseUrl}${path}${buildQueryString(requestOptions.query)}`

    let response: Response
    try {
      response = await doFetch(url, init)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw error
      }
      throw new ApiError(0, null, 'Cannot reach the server')
    }

    if (!response.ok) {
      let problem: Problem | null = null
      if (isJsonContentType(response.headers.get('content-type'))) {
        try {
          problem = (await response.json()) as Problem
        } catch {
          problem = null
        }
      }
      if (response.status === 401) {
        options.onUnauthorized?.()
      }
      throw new ApiError(response.status, problem, problemMessage(response.status, problem))
    }

    if (response.status === 204) {
      return { status: response.status, data: undefined as T }
    }
    const text = await response.text()
    return { status: response.status, data: (text === '' ? undefined : JSON.parse(text)) as T }
  }

  return {
    requestWithStatus,
    async request<T>(method: string, path: string, requestOptions?: RequestOptions): Promise<T> {
      return (await requestWithStatus<T>(method, path, requestOptions)).data
    },
  }
}
