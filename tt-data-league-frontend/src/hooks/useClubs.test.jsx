import { renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../context/AuthContext.jsx'
import { useClubMatches } from './useClubs.js'

function competitionResponse(competition, season, matches) {
  return {
    ok: true,
    headers: { get: () => 'application/json' },
    json: async () => ({
      clubId: 'club-id',
      clubName: 'Club A',
      source: 'RFETM',
      competition,
      season,
      matches,
    }),
  }
}

function Wrapper({ children }) {
  return <MemoryRouter><AuthProvider>{children}</AuthProvider></MemoryRouter>
}

describe('useClubMatches', () => {
  afterEach(() => vi.restoreAllMocks())

  it('fetches every competition in parallel and groups the resulting matches', async () => {
    const requests = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((path) => new Promise((resolve) => {
      requests.push({ path, resolve })
    }))

    const competitions = [
      { name: 'Preferent', season: '2024-2025' },
      { name: 'Copa', season: '2023-2024' },
    ]
    const hook = renderHook(
      () => useClubMatches('club-id', competitions),
      { wrapper: Wrapper },
    )

    await waitFor(() => expect(requests).toHaveLength(2))
    requests[0].resolve(competitionResponse('Preferent', '2024-2025', [{ id: 'm1', homeTeam: 'A', awayTeam: 'B', result: 'win', round: 1 }]))
    requests[1].resolve(competitionResponse('Copa', '2023-2024', [{ id: 'm2', homeTeam: 'A', awayTeam: 'C', result: 'loss', round: 1 }]))

    await waitFor(() => expect(hook.result.current.data).toHaveLength(2))
    expect(hook.result.current.data[0].competition).toBe('Preferent')
    expect(hook.result.current.data[1].competition).toBe('Copa')
    expect(hook.result.current.loading).toBe(false)
    expect(hook.result.current.error).toBeNull()
  })

  it('does not re-fetch when the competition set is unchanged across renders', async () => {
    const requests = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((path) => new Promise((resolve) => {
      requests.push({ path, resolve })
    }))

    const hook = renderHook(
      ({ competitions }) => useClubMatches('club-id', competitions),
      {
        initialProps: { competitions: [{ name: 'Preferent', season: '2024-2025' }] },
        wrapper: Wrapper,
      },
    )

    await waitFor(() => expect(requests).toHaveLength(1))
    requests[0].resolve(competitionResponse('Preferent', '2024-2025', []))
    await waitFor(() => expect(hook.result.current.loading).toBe(false))

    hook.rerender({ competitions: [{ name: 'Preferent', season: '2024-2025' }] })

    expect(requests).toHaveLength(1)
  })

  it('re-fetches when the competition set changes', async () => {
    const requests = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((path) => new Promise((resolve) => {
      requests.push({ path, resolve })
    }))

    const hook = renderHook(
      ({ competitions }) => useClubMatches('club-id', competitions),
      {
        initialProps: { competitions: [{ name: 'Preferent', season: '2024-2025' }] },
        wrapper: Wrapper,
      },
    )

    await waitFor(() => expect(requests).toHaveLength(1))
    requests[0].resolve(competitionResponse('Preferent', '2024-2025', []))
    await waitFor(() => expect(hook.result.current.loading).toBe(false))

    hook.rerender({ competitions: [{ name: 'Copa', season: '2023-2024' }] })

    await waitFor(() => expect(requests).toHaveLength(2))
  })

  it('propagates an error when one of the competition fetches fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((path) => {
      if (path.includes('Copa')) {
        return Promise.resolve({ ok: false, status: 500, headers: { get: () => 'application/json' }, json: async () => ({}) })
      }
      return Promise.resolve(competitionResponse('Preferent', '2024-2025', []))
    })

    const competitions = [
      { name: 'Preferent', season: '2024-2025' },
      { name: 'Copa', season: '2023-2024' },
    ]
    const hook = renderHook(
      () => useClubMatches('club-id', competitions),
      { wrapper: Wrapper },
    )

    await waitFor(() => expect(hook.result.current.error).not.toBeNull())
    expect(hook.result.current.data).toBeNull()
  })
})
