import type { RunStatus, RunTrigger, ScopeType, StepKind, StepStatus } from '../api/types'

/** Mirror of the core `RunStatus.isActive` set. */
export const ACTIVE_STATUSES: readonly RunStatus[] = ['QUEUED', 'RUNNING_INGEST', 'PACKED', 'IMPORTING']
export const TERMINAL_STATUSES: readonly RunStatus[] = ['NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED']
export const STEP_ORDER: readonly StepKind[] = ['INGEST', 'FETCH_PACKAGE', 'IMPORT']

export type ChipColor = 'default' | 'success' | 'warning' | 'error' | 'info'

export const STATUS_LABELS: Readonly<Record<RunStatus, string>> = {
  QUEUED: 'Queued',
  RUNNING_INGEST: 'Running ingest',
  NO_CHANGES: 'No changes',
  PACKED: 'Packed',
  IMPORTING: 'Importing',
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partial',
  FAILED: 'Failed',
}

export const TRIGGER_LABELS: Readonly<Record<RunTrigger, string>> = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled',
  RETRY: 'Retry',
}

export const STEP_LABELS: Readonly<Record<StepKind, string>> = {
  INGEST: 'Ingest',
  FETCH_PACKAGE: 'Fetch package',
  IMPORT: 'Import',
}

export const STEP_STATUS_LABELS: Readonly<Record<StepStatus, string>> = {
  RUNNING: 'running',
  SUCCEEDED: 'succeeded',
  FAILED: 'failed',
}

export const SCOPE_TYPE_LABELS: Readonly<Record<ScopeType, string>> = {
  OPEN_MATCH_DAYS: 'Open match days',
  GROUP: 'Group',
  FULL_SEASON: 'Full season',
}

export function isActiveStatus(status: RunStatus): boolean {
  return ACTIVE_STATUSES.includes(status)
}

export function statusColor(status: RunStatus): ChipColor {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'PARTIAL':
      return 'warning'
    case 'FAILED':
      return 'error'
    case 'NO_CHANGES':
      return 'default'
    default:
      return 'info'
  }
}

export function stepStatusColor(status: StepStatus): ChipColor {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return 'info'
  }
}
