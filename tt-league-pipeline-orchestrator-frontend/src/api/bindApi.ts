import type { HttpClient } from './client'
import * as matchDays from './matchDays'
import * as pendingTriggers from './pendingTriggers'
import * as polling from './polling'
import * as runs from './runs'

type Bound<M> = {
  [K in keyof M]: M[K] extends (client: HttpClient, ...args: infer A) => infer R ? (...args: A) => R : never
}

function bind<M extends object>(module: M, client: HttpClient): Bound<M> {
  const bound: Record<string, unknown> = {}
  for (const [name, fn] of Object.entries(module)) {
    if (typeof fn === 'function') {
      bound[name] = (...args: unknown[]) => (fn as (...a: unknown[]) => unknown)(client, ...args)
    }
  }
  return bound as Bound<M>
}

export interface Api {
  readonly runs: Bound<typeof runs>
  readonly pendingTriggers: Bound<typeof pendingTriggers>
  readonly matchDays: Bound<typeof matchDays>
  readonly polling: Bound<typeof polling>
}

/** Endpoint modules bound to an orchestrator client. */
export function bindApi(client: HttpClient): Api {
  return {
    runs: bind(runs, client),
    pendingTriggers: bind(pendingTriggers, client),
    matchDays: bind(matchDays, client),
    polling: bind(polling, client),
  }
}
