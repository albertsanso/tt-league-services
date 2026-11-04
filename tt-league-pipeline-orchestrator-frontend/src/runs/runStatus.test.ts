import type { RunStatus, UnitStatus } from '../api/types'
import {
  ACTIVE_STATUSES,
  STEP_ORDER,
  TERMINAL_STATUSES,
  TRIGGER_LABELS,
  UNIT_STATUS_LABELS,
  isActiveStatus,
  isRunningUnitStatus,
  isTerminalUnitStatus,
  statusColor,
  stepStatusColor,
  unitStatusColor,
} from './runStatus'

describe('run status', () => {
  it.each<[RunStatus, string, boolean]>([
    ['QUEUED', 'info', true],
    ['RUNNING', 'info', true],
    ['NO_CHANGES', 'default', false],
    ['SUCCEEDED', 'success', false],
    ['PARTIAL', 'warning', false],
    ['FAILED', 'error', false],
  ])('%s has colour %s and active=%s', (status, color, active) => {
    expect(statusColor(status)).toBe(color)
    expect(isActiveStatus(status)).toBe(active)
  })

  it('partitions every status into active or terminal', () => {
    expect([...ACTIVE_STATUSES, ...TERMINAL_STATUSES]).toHaveLength(6)
    expect(STEP_ORDER).toEqual(['INGEST', 'FETCH_PACKAGE', 'IMPORT'])
  })

  it.each<[UnitStatus, string, boolean, boolean]>([
    ['PENDING', 'default', false, false],
    ['RUNNING_INGEST', 'info', true, false],
    ['PACKED', 'info', true, false],
    ['IMPORTING', 'info', true, false],
    ['NO_CHANGES', 'default', false, true],
    ['SUCCEEDED', 'success', false, true],
    ['PARTIAL', 'warning', false, true],
    ['FAILED', 'error', false, true],
    ['SKIPPED', 'default', false, true],
  ])('unit status %s has colour %s, running=%s and terminal=%s', (status, color, running, terminal) => {
    expect(unitStatusColor(status)).toBe(color)
    expect(isRunningUnitStatus(status)).toBe(running)
    expect(isTerminalUnitStatus(status)).toBe(terminal)
    expect(UNIT_STATUS_LABELS[status]).not.toBe('')
  })

  it('colours step statuses', () => {
    expect(stepStatusColor('RUNNING')).toBe('info')
    expect(stepStatusColor('SUCCEEDED')).toBe('success')
    expect(stepStatusColor('FAILED')).toBe('error')
  })

  it('labels a RETRY run as a replay', () => {
    expect(TRIGGER_LABELS.RETRY).toBe('Replay')
    expect(TRIGGER_LABELS.UNIT_RETRY).toBe('Unit retry')
    expect(TRIGGER_LABELS.MANUAL).toBe('Manual')
  })
})
