import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { RunDetail, RunUnit } from '../api/types'
import { useApi } from '../api/useApi'
import { useRunEvents } from '../events/useRunEvents'
import { patchUnit, upsertStep } from './format'
import { isActiveStatus, isTerminalUnitStatus } from './runStatus'

export interface RunDetailResult {
  readonly run: RunDetail | null
  readonly loading: boolean
  readonly error: string | null
  readonly notFound: boolean
  readonly refetch: () => void
}

interface Settled {
  readonly runId: string | null
  readonly run: RunDetail | null
  readonly error: string | null
  readonly notFound: boolean
}

/** Loads one run and keeps it current from the event stream (see the step 9 rules of FEAT-00110). */
export function useRunDetail(runId: string): RunDetailResult {
  const api = useApi()
  const [settled, setSettled] = useState<Settled>({ runId: null, run: null, error: null, notFound: false })
  const [refreshing, setRefreshing] = useState(false)
  const inflightRef = useRef<AbortController | null>(null)
  const staleRef = useRef(false)
  const runIdRef = useRef(runId)

  const start = useCallback(
    (id: string) => {
      inflightRef.current?.abort()
      const controller = new AbortController()
      inflightRef.current = controller
      void (async () => {
        try {
          for (;;) {
            staleRef.current = false
            const run = await api.runs.getRun(id, controller.signal)
            if (controller.signal.aborted) {
              return
            }
            if (staleRef.current) {
              continue
            }
            setSettled({ runId: id, run, error: null, notFound: false })
            break
          }
        } catch (failure: unknown) {
          if (controller.signal.aborted) {
            return
          }
          if (failure instanceof ApiError && (failure.status === 404 || failure.status === 400)) {
            setSettled({ runId: id, run: null, error: null, notFound: true })
          } else {
            const message = failure instanceof Error ? failure.message : 'Failed to load the run'
            setSettled((previous) => ({ ...previous, runId: id, error: message, notFound: false }))
          }
        }
        if (inflightRef.current === controller) {
          inflightRef.current = null
          setRefreshing(false)
        }
      })()
    },
    [api],
  )

  const refetch = useCallback(() => {
    if (inflightRef.current !== null) {
      staleRef.current = true
      return
    }
    setRefreshing(true)
    start(runIdRef.current)
  }, [start])

  useEffect(() => {
    runIdRef.current = runId
    start(runId)
    return () => {
      inflightRef.current?.abort()
      inflightRef.current = null
    }
  }, [runId, start])

  useRunEvents((event) => {
    if (event.type === 'reconnected') {
      refetch()
      return
    }
    if (event.type === 'step') {
      const step = event.payload
      if (step.runId !== runId) {
        return
      }
      setSettled((previous) =>
        previous.run === null
          ? previous
          : {
              ...previous,
              run: {
                ...previous.run,
                steps: upsertStep(previous.run.steps, step),
                units: previous.run.units.map((unit) =>
                  unit.id === step.unitId ? { ...unit, steps: upsertStep(unit.steps, step) } : unit,
                ),
              },
            },
      )
      if (inflightRef.current !== null) {
        staleRef.current = true
      }
      return
    }
    if (event.type === 'unit') {
      const unit = event.payload
      if (unit.runId !== runId) {
        return
      }
      setSettled((previous) =>
        previous.run === null ? previous : { ...previous, run: { ...previous.run, units: patchUnit(previous.run.units, unit) } },
      )
      if (inflightRef.current !== null) {
        staleRef.current = true
      } else if (isTerminalUnitStatus(unit.status)) {
        // The counters, the artifacts and whether the unit can be retried only come from the GET.
        refetch()
      }
      return
    }
    if (event.type === 'run' && event.payload.id === runId) {
      const { units: changedUnits, ...changed } = event.payload
      setSettled((previous) =>
        previous.run === null
          ? previous
          : {
              ...previous,
              run: {
                ...previous.run,
                ...changed,
                importJobReused: changed.importJobReused ?? previous.run.importJobReused,
                units: (changedUnits ?? []).reduce<RunDetail['units']>(
                  (units, unit: RunUnit) => patchUnit(units, unit),
                  previous.run.units,
                ),
              },
            },
      )
      if (inflightRef.current !== null) {
        staleRef.current = true
      } else if (!isActiveStatus(changed.status)) {
        // Artifacts, the import report, the issues and the retry and replay decisions only come from the GET.
        refetch()
      }
    }
  })

  return {
    run: settled.runId === runId ? settled.run : null,
    loading: settled.runId !== runId || refreshing,
    error: settled.runId === runId ? settled.error : null,
    notFound: settled.runId === runId && settled.notFound,
    refetch,
  }
}
