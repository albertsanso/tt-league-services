import type { Problem, TriggerResult } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | null

  constructor(status: number, problem: Problem | null, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export function problemMessage(status: number, problem: Problem | null): string {
  return problem?.detail || problem?.message || problem?.title || `Request failed (${status})`
}

/** Per-source results carried by a 409/422 trigger rejection. */
export function triggerResults(error: unknown): readonly TriggerResult[] {
  return error instanceof ApiError ? (error.problem?.results ?? []) : []
}
