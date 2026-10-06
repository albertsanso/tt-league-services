import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { PollSchedule } from '../api/types'
import { Can } from '../auth/Can'
import { filterText, formatInstant } from '../runs/format'
import { formatIsoDuration, LEVEL_LABELS } from './format'

interface ScheduleTableProps {
  readonly schedules: readonly PollSchedule[]
  readonly onResume: (schedule: PollSchedule) => void
}

function unitLabel(schedule: PollSchedule): string {
  if (schedule.kind === 'FULL_REFRESH' || schedule.filter === null) {
    return 'Full refresh (whole season)'
  }
  const text = filterText(schedule.filter)
  return text === '' ? 'Group' : text
}

/** The poll schedules as the server holds them: level, interval, next run and stop state are shown as sent. */
export function ScheduleTable({ schedules, onResume }: ScheduleTableProps) {
  return (
    <TableContainer>
      <Table size="small" aria-label="Poll schedules">
        <TableHead>
          <TableRow>
            <TableCell>Kind</TableCell>
            <TableCell>Unit</TableCell>
            <TableCell>Level</TableCell>
            <TableCell align="right">Interval</TableCell>
            <TableCell align="right">No changes</TableCell>
            <TableCell>Next run</TableCell>
            <TableCell>Last run</TableCell>
            <TableCell>Pending run</TableCell>
            <TableCell>Stop state</TableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {schedules.map((schedule) => {
            const stopped = schedule.stoppedAt !== null
            return (
              <TableRow
                key={schedule.id}
                data-stopped={stopped ? 'true' : undefined}
                sx={stopped ? { bgcolor: 'action.hover' } : undefined}
              >
                <TableCell>{schedule.kind === 'FULL_REFRESH' ? 'Full refresh' : 'Group'}</TableCell>
                <TableCell>{unitLabel(schedule)}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={stopped ? 'error' : 'default'}
                    variant={stopped ? 'filled' : 'outlined'}
                    label={LEVEL_LABELS[schedule.level]}
                  />
                </TableCell>
                <TableCell align="right">{formatIsoDuration(schedule.interval)}</TableCell>
                <TableCell align="right">{schedule.consecutiveNoChange}</TableCell>
                <TableCell>{formatInstant(schedule.nextRunAt)}</TableCell>
                <TableCell>{formatInstant(schedule.lastRunAt)}</TableCell>
                <TableCell>
                  {schedule.pendingRunId === null ? (
                    '—'
                  ) : (
                    <Link component={RouterLink} to={`/runs/${schedule.pendingRunId}`}>
                      Open run
                    </Link>
                  )}
                </TableCell>
                <TableCell>
                  {stopped ? (
                    <Typography variant="body2">
                      {`${schedule.stopReason ?? 'Stopped'} · ${formatInstant(schedule.stoppedAt)}`}
                    </Typography>
                  ) : (
                    '—'
                  )}
                </TableCell>
                <TableCell align="right">
                  {stopped && (
                    <Can capability="resume-schedules" mode="disable">
                      <Button size="small" onClick={() => onResume(schedule)} aria-label={`Resume ${unitLabel(schedule)}`}>
                        Resume
                      </Button>
                    </Can>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
