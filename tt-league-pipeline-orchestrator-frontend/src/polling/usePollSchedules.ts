import { useCallback, useEffect, useState } from 'react'
import type { PipelineSource, PollSchedule } from '../api/types'
import { useApi } from '../api/useApi'
import { useRunEvents } from '../events/useRunEvents'

export interface PollSchedulesResult {
  readonly schedules: readonly PollSchedule[]
  readonly loading: boolean
  readonly error: string | null
  readonly reload: () => void
}

interface Settled {
  readonly key: string
  readonly schedules: readonly PollSchedule[]
  readonly error: string | null
}

/**
 * Loads the poll schedules of a source and season (nothing is sent while the season is unknown) and refetches when a
 * `run` event of that source arrives, since a run is what moves a schedule, and after a reconnect. It uses the shared
 * event stream and never opens its own connection. The previous list stays visible while a reload is in flight.
 */
export function usePollSchedules(source: PipelineSource, season: string | null): PollSchedulesResult {
  const api = useApi()
  const key = season === null ? null : `${source}|${season}`
  const [reloads, setReloads] = useState(0)
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (key === null || season === null) {
      return undefined
    }
    const controller = new AbortController()
    const requestKey = `${key}#${reloads}`
    api.polling
      .listPollSchedules({ source, season }, controller.signal)
      .then((schedules) => {
        if (!controller.signal.aborted) {
          setSettled({ key: requestKey, schedules, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        const message = failure instanceof Error ? failure.message : 'Failed to load the poll schedules'
        // A failed reload keeps the list of the same source and season, never one of another.
        setSettled((previous) => ({
          key: requestKey,
          schedules: previous?.key.startsWith(`${key}#`) ? previous.schedules : [],
          error: message,
        }))
      })
    return () => controller.abort()
  }, [api, key, reloads, source, season])

  const reload = useCallback(() => setReloads((count) => count + 1), [])
  useRunEvents((event) => {
    if (key === null) {
      return
    }
    if (event.type === 'reconnected' || (event.type === 'run' && event.payload.source === source)) {
      reload()
    }
  })

  const current = key === null ? null : `${key}#${reloads}`
  return {
    // The list of another source or season is never shown under this one.
    schedules: key !== null && settled?.key.startsWith(`${key}#`) ? settled.schedules : [],
    loading: current !== null && settled?.key !== current,
    error: current !== null && settled?.key === current ? settled.error : null,
    reload,
  }
}
