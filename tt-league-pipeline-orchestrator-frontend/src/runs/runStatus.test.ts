import type { RunStatus } from '../api/types'
import { ACTIVE_STATUSES, STEP_ORDER, TERMINAL_STATUSES, isActiveStatus, statusColor, stepStatusColor } from './runStatus'

describe('run status', () => {
  it.each<[RunStatus, string, boolean]>([
    ['QUEUED', 'info', true],
    ['RUNNING_INGEST', 'info', true],
    ['PACKED', 'info', true],
    ['IMPORTING', 'info', true],
    ['NO_CHANGES', 'default', false],
    ['SUCCEEDED', 'success', false],
    ['PARTIAL', 'warning', false],
    ['FAILED', 'error', false],
  ])('%s has colour %s and active=%s', (status, color, active) => {
    expect(statusColor(status)).toBe(color)
    expect(isActiveStatus(status)).toBe(active)
  })

  it('partitions every status into active or terminal', () => {
    expect([...ACTIVE_STATUSES, ...TERMINAL_STATUSES]).toHaveLength(8)
    expect(STEP_ORDER).toEqual(['INGEST', 'FETCH_PACKAGE', 'IMPORT'])
  })

  it('colours step statuses', () => {
    expect(stepStatusColor('RUNNING')).toBe('info')
    expect(stepStatusColor('SUCCEEDED')).toBe('success')
    expect(stepStatusColor('FAILED')).toBe('error')
  })
})
