import Box from '@mui/material/Box'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { RunUnit } from '../api/types'
import { elapsedMs, formatDuration, progressText } from './format'
import { isRunningUnitStatus } from './runStatus'
import { UnitProgressBar } from './UnitProgressBar'
import { UnitStatusChip } from './UnitStatusChip'

/** The units of a run, one compact line each: status, label, duration, error code and the running unit progress. */
export function RunUnitsList({ units, now }: { readonly units: readonly RunUnit[]; readonly now: number }) {
  if (units.length === 0) {
    return <Typography color="text.secondary">No units yet.</Typography>
  }
  return (
    <Box component="ul" aria-label="Units" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 0.5 }}>
      {units.map((unit) => {
        const running = isRunningUnitStatus(unit.status)
        const duration = running ? elapsedMs(unit.startedAt, unit.finishedAt, now) : unit.durationMs
        return (
          <Box component="li" key={unit.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
            <UnitStatusChip status={unit.status} />
            <Typography variant="body2">{unit.label}</Typography>
            <Typography variant="caption" color="text.secondary">
              {formatDuration(duration)}
            </Typography>
            {unit.error !== null && (
              <Tooltip title={unit.error.message}>
                <Typography variant="caption" color="text.secondary">
                  {unit.error.code}
                </Typography>
              </Tooltip>
            )}
            {unit.progress !== null && (
              <Tooltip title={progressText(unit.progress)}>
                <Box>
                  <UnitProgressBar progress={unit.progress} compact />
                </Box>
              </Tooltip>
            )}
          </Box>
        )
      })}
    </Box>
  )
}
