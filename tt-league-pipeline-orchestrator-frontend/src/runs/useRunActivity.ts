import { useRef, useState } from 'react'
import type { RunStatus, StepEvent } from '../api/types'
import { useRunEvents } from '../events/useRunEvents'
import { STATUS_LABELS, STEP_LABELS } from './runStatus'

export const ACTIVITY_LIMIT = 200

export type ActivitySeverity = 'info' | 'success' | 'warning' | 'error'

export interface ActivityEntry {
  readonly id: number
  readonly receivedAt: number
  readonly runId?: string
  readonly source: string | null
  readonly severity: ActivitySeverity
  readonly text: string
}

function runSeverity(status: RunStatus): ActivitySeverity {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'PARTIAL':
      return 'warning'
    case 'FAILED':
      return 'error'
    default:
      return 'info'
  }
}

function stepText(step: StepEvent): string {
  const attempt = step.attempt > 1 ? ` (attempt ${step.attempt})` : ''
  const label = STEP_LABELS[step.kind]
  if (step.status === 'RUNNING') {
    return `${label} started${attempt}`
  }
  if (step.status === 'SUCCEEDED') {
    return `${label} succeeded${attempt}${step.outcome ? ` — ${step.outcome}` : ''}`
  }
  return `${label} failed${attempt}${step.error ? ` — ${step.error.code}: ${step.error.message}` : ''}`
}

/**
 * Builds the in-browser activity log from the event stream: only events received while mounted, newest first, at
 * most {@link ACTIVITY_LIMIT} entries. With `runId` only that run's entries are kept.
 */
export function useRunActivity(options: { runId?: string } = {}): readonly ActivityEntry[] {
  const { runId: onlyRunId } = options
  const [entries, setEntries] = useState<readonly ActivityEntry[]>([])
  const nextId = useRef(0)
  const lastStatus = useRef(new Map<string, RunStatus>())
  const sources = useRef(new Map<string, string>())
  const seenSteps = useRef(new Set<string>())

  const add = (entry: Omit<ActivityEntry, 'id' | 'receivedAt'>) => {
    const full: ActivityEntry = { ...entry, id: nextId.current++, receivedAt: Date.now() }
    setEntries((previous) => [full, ...previous].slice(0, ACTIVITY_LIMIT))
  }

  useRunEvents((event) => {
    switch (event.type) {
      case 'run': {
        const run = event.payload
        if (onlyRunId !== undefined && run.id !== onlyRunId) {
          return
        }
        sources.current.set(run.id, run.source)
        if (lastStatus.current.get(run.id) === run.status) {
          return
        }
        lastStatus.current.set(run.id, run.status)
        const error = run.error ? ` — ${run.error.code}: ${run.error.message}` : ''
        add({
          runId: run.id,
          source: run.source,
          severity: runSeverity(run.status),
          text: `Run ${STATUS_LABELS[run.status].toLowerCase()}${error}`,
        })
        return
      }
      case 'step': {
        const step = event.payload
        if (onlyRunId !== undefined && step.runId !== onlyRunId) {
          return
        }
        const key = `${step.runId}|${step.kind}|${step.attempt}|${step.status}`
        if (seenSteps.current.has(key)) {
          return
        }
        seenSteps.current.add(key)
        add({
          runId: step.runId,
          source: sources.current.get(step.runId) ?? null,
          severity: step.status === 'FAILED' ? 'error' : step.status === 'SUCCEEDED' ? 'success' : 'info',
          text: stepText(step),
        })
        return
      }
      case 'pending-trigger': {
        const trigger = event.payload
        if (onlyRunId !== undefined && trigger.runId !== onlyRunId) {
          return
        }
        const text =
          trigger.state === 'QUEUED'
            ? `Trigger queued by ${trigger.requestedBy}`
            : trigger.state === 'LAUNCHED'
              ? 'Queued trigger launched run'
              : `Queued trigger dropped${trigger.code ? ` — ${trigger.code}` : ''}`
        add({
          runId: trigger.runId,
          source: trigger.source,
          severity: trigger.state === 'DROPPED' ? 'warning' : 'info',
          text,
        })
        return
      }
      case 'reconnected':
        add({
          source: null,
          severity: 'warning',
          text: 'Connection restored; events sent while disconnected are not shown, the data was reloaded.',
        })
    }
  })

  return entries
}
