import Box from '@mui/material/Box'
import LinearProgress from '@mui/material/LinearProgress'
import Typography from '@mui/material/Typography'
import type { UnitProgress } from '../api/types'
import { progressText } from './format'
import { STEP_LABELS } from './runStatus'

interface UnitProgressBarProps {
  readonly progress: UnitProgress
  /** A thin bar without the text below it, for table rows. */
  readonly compact?: boolean
}

/**
 * The progress the server reports for the step a unit is running: a determinate bar when the total is known
 * (`percent`), an indeterminate one otherwise. The figures are the server ones; nothing is derived here.
 */
export function UnitProgressBar({ progress, compact = false }: UnitProgressBarProps) {
  const known = progress.percent !== null
  const step = STEP_LABELS[progress.step]
  const text = progressText(progress)
  const label = known ? `${step}: ${progress.percent}%${text ? ` (${text})` : ''}` : `${step}${text ? `: ${text}` : ''}`
  return (
    <Box sx={{ minWidth: compact ? 120 : 220 }}>
      <LinearProgress
        aria-label={`${step} progress`}
        variant={known ? 'determinate' : 'indeterminate'}
        value={known ? (progress.percent ?? 0) : undefined}
        sx={{ height: compact ? 4 : 6, borderRadius: 3 }}
      />
      {!compact && (
        <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.25 }}>
          {label}
        </Typography>
      )}
    </Box>
  )
}
