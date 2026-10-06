import { useCallback, useEffect, useState } from 'react'
import type { PollingPolicy, PollingStatus } from '../api/types'
import { useApi } from '../api/useApi'

export interface PollingOverview {
  readonly status: PollingStatus
  readonly policies: readonly PollingPolicy[]
}

export interface PollingOverviewResult {
  readonly data: PollingOverview | null
  readonly loading: boolean
  readonly error: string | null
  readonly reload: () => void
}

interface Settled {
  readonly request: number
  readonly data: PollingOverview | null
  readonly error: string | null
}

/**
 * Loads how each source is polled and its effective policy, once on open and again on `reload`. The previous data
 * stays visible while a reload is in flight and an unmount aborts the requests. There is no event subscription:
 * policies change only through this page.
 */
export function usePollingOverview(): PollingOverviewResult {
  const api = useApi()
  const [request, setRequest] = useState(0)
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([api.polling.getPollingStatus(controller.signal), api.polling.listPollingPolicies(controller.signal)])
      .then(([status, policies]) => {
        if (!controller.signal.aborted) {
          setSettled({ request, data: { status, policies }, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        controller.abort()
        const message = failure instanceof Error ? failure.message : 'Failed to load the polling configuration'
        setSettled((previous) => ({ request, data: previous?.data ?? null, error: message }))
      })
    return () => controller.abort()
  }, [api, request])

  const reload = useCallback(() => setRequest((count) => count + 1), [])
  return {
    data: settled?.data ?? null,
    loading: settled?.request !== request,
    error: settled?.request === request ? settled.error : null,
    reload,
  }
}
