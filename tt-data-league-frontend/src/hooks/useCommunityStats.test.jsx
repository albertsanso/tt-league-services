import { renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../context/AuthContext.jsx'
import { EMPTY_STATS, MOCK_STATS, useCommunityStats } from './useCommunityStats.js'

function Wrapper({ children }) {
  return <MemoryRouter><AuthProvider>{children}</AuthProvider></MemoryRouter>
}

function response(body) {
  return {
    ok: true,
    headers: { get: () => 'application/json' },
    json: async () => body,
  }
}

describe('useCommunityStats', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllEnvs()
  })

  it('uses the explicit mock path only when enabled', async () => {
    vi.stubEnv('VITE_USE_MOCK_STATS', 'true')
    const fetchMock = vi.spyOn(globalThis, 'fetch')

    const hook = renderHook(() => useCommunityStats(), { wrapper: Wrapper })

    await waitFor(() => expect(hook.result.current.loading).toBe(false))
    expect(hook.result.current.stats).toEqual(MOCK_STATS)
    expect(hook.result.current.error).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads and normalizes real statistics by default', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({
      players: { total: 10 },
      clubs: { total: 5 },
      matches: { total: 20 },
      season: { name: '2025-2026', status: 'IN_PROGRESS' },
    }))

    const hook = renderHook(() => useCommunityStats(), { wrapper: Wrapper })

    await waitFor(() => expect(hook.result.current.loading).toBe(false))
    expect(hook.result.current.stats.players.total).toBe(10)
    expect(hook.result.current.stats.season.name).toBe('2025-2026')
    expect(hook.result.current.error).toBeNull()
  })

  it('falls back to mock data and reports the error when the request fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 500,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'boom' }),
    })

    const hook = renderHook(() => useCommunityStats(), { wrapper: Wrapper })

    await waitFor(() => expect(hook.result.current.loading).toBe(false))
    expect(hook.result.current.stats).toEqual(EMPTY_STATS)
    expect(hook.result.current.error).not.toBeNull()
    expect(hook.result.current.unauthorized).toBe(false)
  })

  it('marks the error as unauthorized on a 401 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'Unauthorized' }),
    })

    const hook = renderHook(() => useCommunityStats(), { wrapper: Wrapper })

    await waitFor(() => expect(hook.result.current.loading).toBe(false))
    expect(hook.result.current.unauthorized).toBe(true)
  })
})
