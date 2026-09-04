import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getImportPreviewStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'
import { normalizeImportPreview, useImportPreviewStatus } from './useImportPreviewStatus.js'

vi.mock('../api/importJobs.js', () => ({ getImportPreviewStatus: vi.fn() }))
vi.mock('../context/useAuth.js', () => ({ useAuth: vi.fn() }))

describe('useImportPreviewStatus', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(() => vi.clearAllMocks())

  it('normalizes wrapped preview responses', () => {
    expect(normalizeImportPreview({
      response: {
        importResourceId: 'resource-1',
        status: 'success',
        validationFindings: [{ severity: 'info', message: 'ok' }],
        processingErrors: [],
        filesSeen: 2,
        itemsDispatched: 1,
      },
    })).toMatchObject({
      importResourceId: 'resource-1',
      status: 'success',
      validationFindings: [{ severity: 'info', message: 'ok' }],
      filesSeen: 2,
      itemsDispatched: 1,
      skipped: 0,
    })
  })

  it('loads the selected preview status and aborts stale requests', async () => {
    getImportPreviewStatus.mockResolvedValue({ response: { importResourceId: 'one', status: 'empty-result' } })
    const hook = renderHook(({ id }) => useImportPreviewStatus(id), { initialProps: { id: 'one' } })

    await act(async () => {})
    expect(getImportPreviewStatus).toHaveBeenCalledWith('token', 'one', expect.any(AbortSignal), expect.any(Function))
    hook.rerender({ id: 'two' })
    expect(getImportPreviewStatus.mock.calls[0][2].aborted).toBe(true)
  })
})
