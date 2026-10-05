import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import type { StepStatusSummary } from '../api/types'
import { STEP_LABELS, STEP_ORDER, STEP_STATUS_LABELS, stepStatusColor } from './runStatus'

export function StepBadges({ steps }: { steps: readonly StepStatusSummary[] | undefined }) {
  return (
    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
      {STEP_ORDER.map((kind) => {
        const step = steps?.find((candidate) => candidate.kind === kind)
        const name = STEP_LABELS[kind]
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
