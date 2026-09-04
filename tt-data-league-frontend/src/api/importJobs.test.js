import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createImportPreview,
  getImportPreviewStatus,
  getImportResourcesBySource,
  getImportStatus,
  startImport,
} from './importJobs.js'

describe('import status API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  describe('import resources API', () => {
    beforeEach(() => {
      vi.restoreAllMocks()
    })

    it('requests resources for the explicit source with auth and cancellation', async () => {
      const response = {
        ok: true,
        headers: { get: () => 'application/json' },
        json: async () => ({ response: [] }),
      }
      const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
      const controller = new AbortController()

      await getImportResourcesBySource('session-token', 'RFETM', controller.signal, vi.fn())

      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/administration/import/list_by_source?source=RFETM',
        expect.objectContaining({
          signal: controller.signal,
          headers: expect.objectContaining({ Authorization: expect.any(String) }),
        }),
      )
    })
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

  it('requests the resource-scoped preview status without changing the source status endpoint', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ response: { status: 'success' } }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
    const controller = new AbortController()
    const onUnauthorized = vi.fn()

    await getImportPreviewStatus('session-token', 'resource-1', controller.signal, onUnauthorized)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/administration/import/preview_status?importResourceId=resource-1',
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

  it('sends the import resource ID as the preview query parameter without a JSON body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      headers: { get: () => '' },
    })

    await createImportPreview('session-token', 'resource-1', vi.fn())

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/administration/import/preview?importResourceId=resource-1',
      expect.objectContaining({
        method: 'POST',
        body: undefined,
      }),
    )
  })

  it('sends the import resource ID as the start query parameter without a JSON body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      headers: { get: () => '' },
    })

    await startImport('session-token', 'resource-1', vi.fn())

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/administration/import/start?importResourceId=resource-1',
      expect.objectContaining({
        method: 'POST',
        body: undefined,
      }),
    )
  })
})
