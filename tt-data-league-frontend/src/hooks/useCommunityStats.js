import { useCallback, useEffect, useState } from 'react'
import { getCommunityStatistics } from '../api/stats.js'
import { useAuth } from '../context/useAuth.js'

export const MOCK_STATS = {
  players: { total: 1248 },
  clubs: { total: 186 },
  matches: { total: 8432 },
  season: { name: '24/25', status: 'IN_PROGRESS' },
}

export const EMPTY_STATS = {
  players: { total: 0 },
  clubs: { total: 0 },
  matches: { total: 0 },
  season: { name: null, status: 'UNAVAILABLE' },
}

export function useCommunityStats() {
  const { token, clearSession } = useAuth()
  const [stats, setStats] = useState(EMPTY_STATS)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadStats = useCallback(async (signal) => {
    const useMockData = import.meta.env.VITE_USE_MOCK_STATS === 'true'

    setLoading(true)
    setError(null)

    if (useMockData) {
      window.setTimeout(() => {
        if (signal?.aborted) {
          return
        }

        setStats(MOCK_STATS)
        setLoading(false)
      }, 250)
      return
    }

    try {
      const data = await getCommunityStatistics(token, signal, clearSession)
      setStats(data)
    } catch (requestError) {
      if (requestError.name === 'AbortError' || signal?.aborted) {
        return
      }

      setStats(EMPTY_STATS)
      setError(requestError)
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [clearSession, token])

  useEffect(() => {
    const controller = new AbortController()
    const timeoutId = window.setTimeout(() => {
      void loadStats(controller.signal)
    }, 0)

    return () => {
      window.clearTimeout(timeoutId)
      controller.abort()
    }
  }, [loadStats])

  return { stats, loading, error, unauthorized: error?.status === 401 }
}
