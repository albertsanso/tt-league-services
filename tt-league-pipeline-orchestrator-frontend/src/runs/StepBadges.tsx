import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import type { StepKind, StepStatusSummary } from '../api/types'
import { STEP_LABELS, STEP_ORDER, STEP_STATUS_LABELS, stepStatusColor } from './runStatus'

const REPLAY_SKIPPED: readonly StepKind[] = ['INGEST', 'FETCH_PACKAGE']

/** A replay run skips ingest and the package fetch, so those steps show "skipped (replay)" instead of "not started". */
export function StepBadges({
  steps,
  replay = false,
}: {
  steps: readonly StepStatusSummary[] | undefined
  replay?: boolean
}) {
  return (
    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
      {STEP_ORDER.map((kind) => {
        const step = steps?.find((candidate) => candidate.kind === kind)
        const name = STEP_LABELS[kind]
        if (step === undefined && replay && REPLAY_SKIPPED.includes(kind)) {
          return <Chip key={kind} size="small" variant="outlined" label={`${name}: skipped (replay)`} aria-label={`${name}: skipped (replay)`} />
        }
        if (step === undefined) {
          return <Chip key={kind} size="small" variant="outlined" label={name} aria-label={`${name}: not started`} />
        }
        const status = STEP_STATUS_LABELS[step.status]
        return (
          <Chip
            key={kind}
            size="small"
            color={stepStatusColor(step.status)}
            label={step.attempt > 1 ? `${name} ×${step.attempt}` : name}
            aria-label={`${name}: ${status}, attempt ${step.attempt}`}
            icon={step.status === 'RUNNING' ? <CircularProgress size={12} color="inherit" /> : undefined}
          />
        )
      })}
    </Box>
  )
}
