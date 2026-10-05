import { useEffect, useState } from 'react'

/** Local clock in epoch milliseconds, ticking every `intervalMs` only while `enabled`. */
export function useNow(intervalMs: number, enabled: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) {
      return undefined
    }
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs, enabled])
  return now
}
