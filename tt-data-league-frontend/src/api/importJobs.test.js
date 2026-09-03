import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getImportStatus } from './importJobs.js'

describe('import status API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('requests the wrapped source status endpoint with auth and cancellation', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ sources: [{ sourceName: 'RFETM' }] }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
    const controller = new AbortController()
    const onUnauthorized = vi.fn()

    await getImportStatus('session-token', controller.signal, onUnauthorized)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/administration/import/status',
      expect.objectContaining({
        signal: controller.signal,
        headers: expect.objectContaining({ Authorization: expect.any(String) }),
      }),
    )
  })

  it('propagates failed status responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 503,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'offline' }),
    })

    await expect(getImportStatus('session-token')).rejects.toMatchObject({ status: 503 })
  })
})
