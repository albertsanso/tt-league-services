import { useCallback, useEffect, useState } from 'react'

const MOCK_STATS = {
  jugadors: { total: 1248, delta_temporada: 86 },
  clubs: { total: 186, delta_temporada: 9 },
  partits: { total: 8432, delta_temporada: 1257 },
  temporada: { nom: '24/25', estat: 'en_curs' },
}

function normalizeStats(response) {
  return {
    jugadors: {
      total: Number(response?.jugadors?.total ?? MOCK_STATS.jugadors.total),
      delta_temporada: Number(
        response?.jugadors?.delta_temporada ?? MOCK_STATS.jugadors.delta_temporada,
      ),
    },
    clubs: {
      total: Number(response?.clubs?.total ?? MOCK_STATS.clubs.total),
      delta_temporada: Number(
        response?.clubs?.delta_temporada ?? MOCK_STATS.clubs.delta_temporada,
      ),
    },
    partits: {
      total: Number(response?.partits?.total ?? MOCK_STATS.partits.total),
      delta_temporada: Number(
        response?.partits?.delta_temporada ?? MOCK_STATS.partits.delta_temporada,
      ),
    },
    temporada: {
      nom: response?.temporada?.nom ?? MOCK_STATS.temporada.nom,
      estat: response?.temporada?.estat ?? MOCK_STATS.temporada.estat,
    },
  }
}

export function useCommunityStats() {
  const [stats, setStats] = useState(MOCK_STATS)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadStats = useCallback(async (signal) => {
    const useMockData = import.meta.env.VITE_USE_MOCK_STATS !== 'false'

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
      const response = await fetch('/api/stats/community', { signal })

      if (!response.ok) {
        throw new Error(`Resposta no vàlida: ${response.status}`)
      }

      const data = await response.json()
      setStats(normalizeStats(data))
    } catch {
      if (signal?.aborted) {
        return
      }

      setStats(MOCK_STATS)
      setError('No s\'han pogut carregar les estadístiques en temps real.')
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [])

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

  return { stats, loading, error }
}
