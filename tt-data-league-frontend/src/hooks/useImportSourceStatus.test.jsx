import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getImportStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'
import { normalizeImportStatus, useImportSourceStatus } from './useImportSourceStatus.js'

vi.mock('../api/importJobs.js', () => ({
  getImportStatus: vi.fn(),
}))

vi.mock('../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

describe('useImportSourceStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('normalizes the endpoint response to the three supported sources', () => {
    expect(normalizeImportStatus({
      success: true,
      response: {
        sources: [
          { sourceName: 'RFETM' },
          { sourceName: 'BCNESA' },
          { sourceName: 'FCTT' },
        ],
      },
    })).toEqual([
      { id: 'RFETM', label: 'RFETM', status: 'available' },
      { id: 'BCNESA', label: 'BCNESA', status: 'available' },
      { id: 'FCTT', label: 'FCTT', status: 'available' },
    ])
  })

  it('normalizes a direct status payload for compatibility', () => {
    expect(normalizeImportStatus({
      sources: [{ sourceName: 'FCTT' }, { sourceName: 'OTHER' }],
    })).toEqual([
      { id: 'RFETM', label: 'RFETM', status: 'unavailable' },
      { id: 'BCNESA', label: 'BCNESA', status: 'unavailable' },
      { id: 'FCTT', label: 'FCTT', status: 'available' },
    ])
  })

  it('rejects malformed status payloads', () => {
    expect(() => normalizeImportStatus({ sources: null })).toThrow()
  })

  it('loads immediately, refreshes every five seconds, and avoids overlap', async () => {
    let resolveInitial
    getImportStatus.mockImplementationOnce(() => new Promise((resolve) => {
      resolveInitial = resolve
    }))
    getImportStatus.mockResolvedValue({ sources: [{ sourceName: 'RFETM' }] })

    const hook = renderHook(() => useImportSourceStatus())
    expect(hook.result.current.loading).toBe(true)
    expect(getImportStatus).toHaveBeenCalledTimes(1)

    await act(async () => {
      vi.advanceTimersByTime(10000)
    })
    expect(getImportStatus).toHaveBeenCalledTimes(1)

    await act(async () => {
      resolveInitial({ sources: [{ sourceName: 'RFETM' }] })
      await Promise.resolve()
    })
    expect(hook.result.current.loading).toBe(false)

    await act(async () => {
      vi.advanceTimersByTime(5000)
    })
    expect(getImportStatus).toHaveBeenCalledTimes(2)
    expect(hook.result.current.data[0].status).toBe('available')

    hook.unmount()
    expect(getImportStatus.mock.calls[0][1].aborted).toBe(true)
  })

  it('exposes errors and retries the request', async () => {
    getImportStatus
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ sources: [{ sourceName: 'BCNESA' }] })

    const hook = renderHook(() => useImportSourceStatus())
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(hook.result.current.error).toBeTruthy()
    expect(hook.result.current.data.every((source) => source.status === 'error')).toBe(true)

    act(() => hook.result.current.retry())
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(hook.result.current.data[1].status).toBe('available')
    expect(getImportStatus).toHaveBeenCalledTimes(2)
  })

  it('retains available sources when a later poll fails', async () => {
    getImportStatus
      .mockResolvedValueOnce({ sources: [
        { sourceName: 'RFETM' },
        { sourceName: 'BCNESA' },
        { sourceName: 'FCTT' },
      ] })
      .mockRejectedValueOnce(new Error('offline'))

    const hook = renderHook(() => useImportSourceStatus())
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(hook.result.current.data.every((source) => source.status === 'available')).toBe(true)

    await act(async () => {
      vi.advanceTimersByTime(5000)
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(hook.result.current.error).toBeTruthy()
    expect(hook.result.current.data.every((source) => source.status === 'available')).toBe(true)
  })
})
