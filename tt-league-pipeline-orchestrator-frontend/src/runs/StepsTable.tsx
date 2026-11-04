import Chip from '@mui/material/Chip'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import type { Step, StepHealth } from '../api/types'
import { elapsedMs, formatDuration, formatInstant } from './format'
import { STEP_LABELS, STEP_STATUS_LABELS, stepStatusColor } from './runStatus'

/** The source failures an ingest attempt reported; a dash when the ingest service did not report them. */
function healthText(health: StepHealth | null): string {
  return health === null
    ? '—'
    : `HTTP ${health.httpErrors} · timeouts ${health.timeouts} · parse ${health.parseErrors}`
}

/** Every attempt of the given steps, one row each. */
export function StepsTable({ steps, now, label }: { steps: readonly Step[]; now: number; label: string }) {
  return (
    <TableContainer>
      <Table size="small" aria-label={label}>
        <TableHead>
          <TableRow>
            <TableCell>Step</TableCell>
            <TableCell>Attempt</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Started</TableCell>
            <TableCell>Finished</TableCell>
            <TableCell>Duration</TableCell>
            <TableCell>Reference</TableCell>
            <TableCell>Outcome</TableCell>
            <TableCell>Source health</TableCell>
            <TableCell>Retryable</TableCell>
            <TableCell>Error</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {steps.map((step) => (
            <TableRow key={`${step.unitId}-${step.kind}-${step.attempt}`}>
              <TableCell>{STEP_LABELS[step.kind]}</TableCell>
              <TableCell>{step.attempt}</TableCell>
              <TableCell>
                <Chip size="small" color={stepStatusColor(step.status)} label={STEP_STATUS_LABELS[step.status]} />
              </TableCell>
              <TableCell>{formatInstant(step.startedAt)}</TableCell>
              <TableCell>{formatInstant(step.finishedAt)}</TableCell>
              <TableCell>
                {formatDuration(
                  step.status === 'RUNNING' ? elapsedMs(step.startedAt, step.finishedAt, now) : step.durationMs,
                )}
              </TableCell>
              <TableCell>{step.externalRef ?? '—'}</TableCell>
              <TableCell>{step.outcome ?? '—'}</TableCell>
              <TableCell>{healthText(step.health)}</TableCell>
              <TableCell>{step.retryable === null ? '—' : step.retryable ? 'Yes' : 'No'}</TableCell>
              <TableCell>{step.error ? `${step.error.code}: ${step.error.message}` : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
