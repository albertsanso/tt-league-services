import { useMemo } from 'react'
import type { ReactNode } from 'react'
import { RunEventsContext } from '../events/eventsContext'
import type { ConnectionState } from '../events/eventsContext'
import type { FakeEventBus } from './eventBus'

/** Provides a controllable event stream: `bus.emit(...)` delivers to every subscribed hook. */
export function FakeEvents({
  bus,
  state = 'open',
  children,
}: {
  bus: FakeEventBus
  state?: ConnectionState
  children: ReactNode
}) {
  const value = useMemo(() => ({ connection: { state }, subscribe: bus.subscribe }), [bus, state])
  return <RunEventsContext.Provider value={value}>{children}</RunEventsContext.Provider>
}
