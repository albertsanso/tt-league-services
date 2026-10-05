import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import type { Page, RunSummary } from '../api/types'
import { elapsedMs, formatDuration, formatInstant, scopeDetails, scopeLabel } from './format'
import { RunStatusChip } from './RunStatusChip'
import { isActiveStatus, TRIGGER_LABELS } from './runStatus'
import { StepBadges } from './StepBadges'
import { useNow } from './useNow'

interface RunsTableProps {
  readonly page: Page<RunSummary>
  readonly filtered: boolean
  /** 0-based API page. */
  readonly onPageChange: (page: number) => void
  readonly onClearFilters: () => void
}

function triggerText(run: RunSummary): string {
  if (run.trigger === 'MANUAL') {
    return run.requestedBy ? `Manual · ${run.requestedBy}` : 'Manual'
  }
  return TRIGGER_LABELS[run.trigger]
}

export function RunsTable({ page, filtered, onPageChange, onClearFilters }: RunsTableProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const anyActive = page.items.some((run) => isActiveStatus(run.status))
  const now = useNow(1000, anyActive)

  if (page.items.length === 0 && page.page === 0) {
    return (
      <Box sx={{ py: 2 }}>
        <Typography color="text.secondary">{filtered ? 'No runs match these filters.' : 'No runs yet.'}</Typography>
        {filtered && (
          <Button size="small" onClick={onClearFilters} sx={{ mt: 1 }}>
            Clear filters
          </Button>
        )}
      </Box>
    )
  }

  const open = (id: string) => navigate(`/runs/${id}`, { state: { backTo: location.search } })

  return (
    <>
      <TableContainer>
        <Table size="small" aria-label="Runs">
          <TableHead>
            <TableRow>
              <TableCell>Created</TableCell>
              <TableCell>Source</TableCell>
              <TableCell>Trigger</TableCell>
              <TableCell>Scope</TableCell>
              <TableCell>Duration</TableCell>
              <TableCell>Steps</TableCell>
              <TableCell>Outcome</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {page.items.map((run) => {
              const duration = isActiveStatus(run.status)
                ? elapsedMs(run.startedAt, run.finishedAt, now)
                : (run.durationMs ?? elapsedMs(run.startedAt, run.finishedAt, now))
              return (
                <TableRow key={run.id} hover sx={{ cursor: 'pointer' }} onClick={() => open(run.id)}>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`/runs/${run.id}`}
                      state={{ backTo: location.search }}
                      onClick={(event) => event.stopPropagation()}
                    >
                      {formatInstant(run.createdAt)}
                    </Link>
                  </TableCell>
                  <TableCell>{run.source}</TableCell>
                  <TableCell>
                    {triggerText(run)}
                    {run.force && <Chip size="small" variant="outlined" label="forced" sx={{ ml: 0.5 }} />}
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        <>
                          {scopeDetails(run).map((line) => (
                            <div key={line}>{line}</div>
                          ))}
                        </>
                      }
                    >
                      <span>{scopeLabel(run)}</span>
                    </Tooltip>
                  </TableCell>
                  <TableCell>{formatDuration(duration)}</TableCell>
                  <TableCell>
                    <StepBadges steps={run.steps} />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <RunStatusChip status={run.status} />
                      {run.error !== null && (
                        <Tooltip title={run.error.message}>
                          <Typography variant="caption" color="text.secondary">
                            {run.error.code}
                          </Typography>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={page.totalItems}
        page={page.page}
        rowsPerPage={page.size}
        rowsPerPageOptions={[]}
        onPageChange={(_event, next) => onPageChange(next)}
      />
    </>
  )
}
