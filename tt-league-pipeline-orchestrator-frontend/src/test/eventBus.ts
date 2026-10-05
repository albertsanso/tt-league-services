import { act } from '@testing-library/react'
import type { PipelineEvent, PipelineEventListener } from '../events/eventsContext'

export interface FakeEventBus {
  readonly emit: (event: PipelineEvent) => Promise<void>
  /** Delivers all events inside a single act(), for volume tests. */
  readonly emitAll: (events: readonly PipelineEvent[]) => Promise<void>
  readonly listenerCount: () => number
  readonly subscribe: (listener: PipelineEventListener) => () => void
}

export function createEventBus(): FakeEventBus {
  const listeners = new Set<PipelineEventListener>()
  return {
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    emit: (event) =>
      act(async () => {
        listeners.forEach((listener) => listener(event))
      }),
    emitAll: (events) =>
      act(async () => {
        events.forEach((event) => listeners.forEach((listener) => listener(event)))
      }),
    listenerCount: () => listeners.size,
  }
}
