import type { ReactNode } from 'react'
import { ApiContext } from '../api/apiContext'
import type { Api } from '../api/bindApi'

/** Injects stub endpoint modules; unstubbed modules throw when used. */
export function TestApiProvider({ api, children }: { api: Partial<Api>; children: ReactNode }) {
  const missing = (name: string) =>
    new Proxy({}, {
      get: (_target, property) => () => {
        throw new Error(`TestApiProvider: ${name}.${String(property)} is not stubbed`)
      },
    })
  const full = {
    runs: missing('runs'),
    pendingTriggers: missing('pendingTriggers'),
    matchDays: missing('matchDays'),
    polling: missing('polling'),
    statistics: missing('statistics'),
    ...api,
  } as Api
  return <ApiContext.Provider value={full}>{children}</ApiContext.Provider>
}
