import { useContext, useEffect, useRef } from 'react'
import { RunEventsContext } from './eventsContext'
import type { EventConnection, PipelineEventListener, RunEventsContextValue } from './eventsContext'

function useRunEventsContext(): RunEventsContextValue {
  const value = useContext(RunEventsContext)
  if (value === null) {
    throw new Error('Run events hooks must be used inside a RunEventsProvider')
  }
  return value
}

/** Subscribes the listener for the lifetime of the component; the latest listener is always called. */
export function useRunEvents(listener: PipelineEventListener): void {
  const { subscribe } = useRunEventsContext()
  const listenerRef = useRef(listener)
  useEffect(() => {
    listenerRef.current = listener
  }, [listener])
  useEffect(() => subscribe((event) => listenerRef.current(event)), [subscribe])
}

export function useEventConnection(): EventConnection {
  return useRunEventsContext().connection
}
