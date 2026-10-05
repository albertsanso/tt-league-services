import { ApiError, triggerResults } from './ApiError'
import { createHttpClient } from './client'

function jsonResponse(body: unknown, init: { status?: number; type?: string } = {}): Response {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'Content-Type': init.type ?? 'application/json' },
  })
}

function setup(respond: () => Response | Promise<Response>, token: string | null = 'tok') {
  const fetchMock = vi.fn(async (...args: Parameters<typeof fetch>) => {
    void args
    return respond()
  })
  const onUnauthorized = vi.fn()
  const client = createHttpClient({
    baseUrl: 'http://host',
    getToken: () => token,
    onUnauthorized,
    fetch: fetchMock as unknown as typeof fetch,
  })
  return { client, fetchMock, onUnauthorized }
}

describe('createHttpClient', () => {
  it('sends accept and bearer headers and builds the query', async () => {
    const { client, fetchMock } = setup(() => jsonResponse({ ok: true }))
    await client.request('GET', '/api/pipeline/runs', {
      query: { source: ['RFETM', 'FCTT'], status: undefined, page: 0, empty: '', nothing: null },
    })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://host/api/pipeline/runs?source=RFETM&source=FCTT&page=0')
    const headers = (init as RequestInit).headers as Record<string, string>
    expect(headers.Accept).toBe('application/json')
    expect(headers.Authorization).toBe('Bearer tok')
    expect(headers['Content-Type']).toBeUndefined()
  })

  it('omits the authorization header without a token', async () => {
    const { client, fetchMock } = setup(() => jsonResponse({}), null)
    await client.request('GET', '/x')
    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('serialises a JSON body', async () => {
    const { client, fetchMock } = setup(() => jsonResponse({}))
    await client.request('POST', '/x', { body: { a: 1 } })
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.body).toBe('{"a":1}')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('returns undefined for 204 and for an empty body', async () => {
    const empty = setup(() => new Response(null, { status: 204 }))
    await expect(empty.client.request('DELETE', '/x')).resolves.toBeUndefined()
    const blank = setup(() => new Response('', { status: 200 }))
    await expect(blank.client.request('GET', '/x')).resolves.toBeUndefined()
  })

  it('exposes the status of a successful response', async () => {
    const { client } = setup(() => jsonResponse({ results: [] }, { status: 202 }))
    await expect(client.requestWithStatus('POST', '/x')).resolves.toEqual({ status: 202, data: { results: [] } })
  })

  it('maps a problem body with code, field and results', async () => {
    const problem = {
      title: 'Conflict',
      detail: 'A run is already active',
      status: 409,
      code: 'RUN_ACTIVE',
      field: 'source',
      results: [{ source: 'RFETM', outcome: 'REJECTED' }],
    }
    const { client } = setup(() => jsonResponse(problem, { status: 409, type: 'application/problem+json' }))
    const error = await client.request('POST', '/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(409)
    expect(apiError.message).toBe('A run is already active')
    expect(apiError.problem?.code).toBe('RUN_ACTIVE')
    expect(apiError.problem?.field).toBe('source')
    expect(triggerResults(apiError)).toHaveLength(1)
  })

  it('maps a plain JSON platform error', async () => {
    const { client } = setup(() => jsonResponse({ message: 'Invalid username or password' }, { status: 401 }))
    const error = (await client.request('POST', '/x').catch((e: unknown) => e)) as ApiError
    expect(error.message).toBe('Invalid username or password')
  })

  it('maps a non-JSON error to a generic message', async () => {
    const { client } = setup(() => new Response('boom', { status: 500, headers: { 'Content-Type': 'text/plain' } }))
    const error = (await client.request('GET', '/x').catch((e: unknown) => e)) as ApiError
    expect(error.problem).toBeNull()
    expect(error.message).toBe('Request failed (500)')
    expect(triggerResults(error)).toEqual([])
  })

  it('maps a network failure to status 0', async () => {
    const { client } = setup(() => Promise.reject(new TypeError('failed')))
    const error = (await client.request('GET', '/x').catch((e: unknown) => e)) as ApiError
    expect(error.status).toBe(0)
    expect(error.message).toBe('Cannot reach the server')
  })

  it('rethrows an abort unchanged', async () => {
    const abort = new DOMException('aborted', 'AbortError')
    const { client } = setup(() => Promise.reject(abort))
    await expect(client.request('GET', '/x')).rejects.toBe(abort)
  })

  it('calls onUnauthorized on 401 before throwing', async () => {
    const { client, onUnauthorized } = setup(() => new Response('', { status: 401 }))
    await expect(client.request('GET', '/x')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })
})
