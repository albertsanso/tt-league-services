import type { RunStatus, RunTrigger, ScopeType, StepKind, StepStatus, UnitStatus } from '../api/types'

/** Mirror of the core `RunStatus.isActive` set. */
export const ACTIVE_STATUSES: readonly RunStatus[] = ['QUEUED', 'RUNNING']
export const TERMINAL_STATUSES: readonly RunStatus[] = ['NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED']
export const STEP_ORDER: readonly StepKind[] = ['INGEST', 'FETCH_PACKAGE', 'IMPORT']

export type ChipColor = 'default' | 'success' | 'warning' | 'error' | 'info'

export const STATUS_LABELS: Readonly<Record<RunStatus, string>> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  NO_CHANGES: 'No changes',
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partial',
  FAILED: 'Failed',
}

/** Mirror of the core `UnitStatus`: `isRunning` are the statuses that may carry progress. */
export const UNIT_STATUS_LABELS: Readonly<Record<UnitStatus, string>> = {
  PENDING: 'Pending',
  RUNNING_INGEST: 'Running ingest',
  PACKED: 'Packed',
  IMPORTING: 'Importing',
  NO_CHANGES: 'No changes',
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partial',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
}

const TERMINAL_UNIT_STATUSES: readonly UnitStatus[] = ['NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED']
const RUNNING_UNIT_STATUSES: readonly UnitStatus[] = ['RUNNING_INGEST', 'PACKED', 'IMPORTING']

export const TRIGGER_LABELS: Readonly<Record<RunTrigger, string>> = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled',
  RETRY: 'Replay',
  UNIT_RETRY: 'Unit retry',
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

export function isTerminalUnitStatus(status: UnitStatus): boolean {
  return TERMINAL_UNIT_STATUSES.includes(status)
}

/** The unit is doing work now (not waiting for its turn and not finished). */
export function isRunningUnitStatus(status: UnitStatus): boolean {
  return RUNNING_UNIT_STATUSES.includes(status)
}

export function unitStatusColor(status: UnitStatus): ChipColor {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'PARTIAL':
      return 'warning'
    case 'FAILED':
      return 'error'
    case 'NO_CHANGES':
    case 'PENDING':
    case 'SKIPPED':
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
