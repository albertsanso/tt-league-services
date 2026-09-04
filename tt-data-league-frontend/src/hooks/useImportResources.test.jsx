import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getImportResourcesBySource } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'
import { normalizeImportResources, useImportResources } from './useImportResources.js'

vi.mock('../api/importJobs.js', () => ({ getImportResourcesBySource: vi.fn() }))
vi.mock('../context/useAuth.js', () => ({ useAuth: vi.fn() }))

describe('useImportResources', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(() => vi.clearAllMocks())

  it('normalizes wrapped resource responses', () => {
    expect(normalizeImportResources({ response: [{ importResourceId: 'one', season: '2025' }] }))
      .toEqual([{ id: 'one', filename: null, season: '2025', status: undefined, createdDate: undefined, lastProcessedDate: undefined, resourceType: undefined }])
  })

  it('loads the selected source and aborts on source changes', async () => {
    getImportResourcesBySource.mockResolvedValue({ response: [{ importResourceId: 'one' }] })
    const hook = renderHook(({ source }) => useImportResources(source), { initialProps: { source: 'RFETM' } })

    await act(async () => {})
    expect(getImportResourcesBySource).toHaveBeenCalledWith('token', 'RFETM', expect.any(AbortSignal), expect.any(Function))
    hook.rerender({ source: 'BCNESA' })
    expect(getImportResourcesBySource.mock.calls[0][2].aborted).toBe(true)
  })
})
