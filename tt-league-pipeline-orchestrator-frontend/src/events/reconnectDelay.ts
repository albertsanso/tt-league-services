const MIN_RETRY_MS = 5_000
const MAX_RETRY_MS = 60_000
const JITTER = 0.2

/** Backoff before the next connection attempt: server retry (min 5 s) doubled per failure, capped, with jitter. */
export function reconnectDelay(serverRetryMs: number, consecutiveFailures: number, random: number): number {
  const base = Math.max(serverRetryMs, MIN_RETRY_MS) * 2 ** Math.max(consecutiveFailures - 1, 0)
  const capped = Math.min(base, MAX_RETRY_MS)
  return Math.round(capped * (1 + (random * 2 - 1) * JITTER))
}
