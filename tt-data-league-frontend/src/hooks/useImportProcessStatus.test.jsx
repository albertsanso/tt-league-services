import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getImportRunStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'
import { normalizeImportRunStatus, useImportProcessStatus } from './useImportProcessStatus.js'

vi.mock('../api/importJobs.js', () => ({ getImportRunStatus: vi.fn() }))
vi.mock('../context/useAuth.js', () => ({ useAuth: vi.fn() }))

describe('useImportProcessStatus', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.resetAllMocks()
  })

  it('normalizes a queued/running snapshot with an indeterminate total', () => {
    expect(normalizeImportRunStatus({
      response: {
        runId: 'run-1',
        importResourceId: 'resource-1',
        status: 'RUNNING',
        processed: 3,
        total: null,
        percentage: null,
        skipped: 1,
        errorCount: 0,
        result: null,
      },
    })).toMatchObject({
      runId: 'run-1',
      status: 'running',
      processed: 3,
      total: null,
      percentage: null,
      skipped: 1,
      result: null,
    })
  })

  it('normalizes a terminal snapshot including the nested result', () => {
    expect(normalizeImportRunStatus({
      response: {
        runId: 'run-1',
        status: 'success',
        processed: 2,
        total: 2,
        percentage: 100,
        skipped: 0,
        errorCount: 0,
        result: { status: 'SUCCESS', filesSeen: 2, itemsPersisted: 2, skipped: 0, findings: [], processingErrors: [] },
      },
    })).toMatchObject({
      status: 'success',
      result: { status: 'success', itemsPersisted: 2 },
    })
  })

  it('falls back to failure for an unrecognized status', () => {
    expect(normalizeImportRunStatus({ status: 'unexpected' }).status).toBe('failure')
  })

  it('polls while the run is active and stops once it reaches a terminal state', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    getImportRunStatus
      .mockResolvedValueOnce({ response: { runId: 'run-1', status: 'queued', processed: 0 } })
      .mockResolvedValueOnce({ response: { runId: 'run-1', status: 'running', processed: 1 } })
      .mockResolvedValueOnce({ response: { runId: 'run-1', status: 'success', processed: 2, result: { status: 'success' } } })

    const hook = renderHook(({ id }) => useImportProcessStatus(id), { initialProps: { id: 'run-1' } })

    await waitFor(() => expect(hook.result.current.data?.status).toBe('queued'))
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    await waitFor(() => expect(hook.result.current.data?.status).toBe('running'))
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    await waitFor(() => expect(hook.result.current.data?.status).toBe('success'))

    expect(getImportRunStatus).toHaveBeenCalledTimes(3)
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(getImportRunStatus).toHaveBeenCalledTimes(3)

    hook.unmount()
  })

  it('ignores stale responses and aborts when runId changes', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    getImportRunStatus.mockResolvedValue({ response: { runId: 'one', status: 'running', processed: 1 } })
    const hook = renderHook(({ id }) => useImportProcessStatus(id), { initialProps: { id: 'one' } })

    await act(async () => {})
    expect(getImportRunStatus).toHaveBeenCalledWith('token', 'one', expect.any(AbortSignal), expect.any(Function))
    hook.rerender({ id: 'two' })
    expect(getImportRunStatus.mock.calls[0][2].aborted).toBe(true)

    hook.unmount()
  })

  it('returns an empty state and does not call the API when runId is falsy', () => {
    const hook = renderHook(({ id }) => useImportProcessStatus(id), { initialProps: { id: null } })

    expect(hook.result.current).toMatchObject({ runId: null, data: null, loading: false, error: null })
    expect(getImportRunStatus).not.toHaveBeenCalled()

    hook.unmount()
  })
})
