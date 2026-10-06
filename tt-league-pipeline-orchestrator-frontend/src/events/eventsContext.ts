import { createContext } from 'react'
import type { MatchDaysEvent, PendingTriggerEvent, RunEvent, StepEvent, UnitEvent } from '../api/types'

export type ConnectionState = 'connecting' | 'open' | 'reconnecting' | 'stopped'

export interface EventConnection {
  readonly state: ConnectionState
  /** Epoch milliseconds of the last bytes received, keep-alive comments included. */
  readonly lastEventAt?: number
}

export type PipelineEvent =
  | { readonly type: 'run'; readonly payload: RunEvent }
  | { readonly type: 'step'; readonly payload: StepEvent }
  | { readonly type: 'unit'; readonly payload: UnitEvent }
  | { readonly type: 'pending-trigger'; readonly payload: PendingTriggerEvent }
  | { readonly type: 'match-days'; readonly payload: MatchDaysEvent }
  | { readonly type: 'reconnected' }

export type PipelineEventListener = (event: PipelineEvent) => void

export interface RunEventsContextValue {
  readonly connection: EventConnection
  subscribe(listener: PipelineEventListener): () => void
}

export const RunEventsContext = createContext<RunEventsContextValue | null>(null)
