import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Collapse from '@mui/material/Collapse'
import IconButton from '@mui/material/IconButton'
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
import { Fragment, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import type { Page, RunSummary } from '../api/types'
import { elapsedMs, formatDuration, formatInstant, scopeDetails, scopeLabel, unitsSummary } from './format'
import { RunStatusChip } from './RunStatusChip'
import { RunUnitsList } from './RunUnitsList'
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
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())

  const toggle = (id: string) =>
    setExpanded((previous) => {
      const next = new Set(previous)
      if (!next.delete(id)) {
        next.add(id)
      }
      return next
    })

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
              <TableCell padding="checkbox">
                <span style={{ position: 'absolute', left: -9999 }}>Units</span>
              </TableCell>
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
              const units = run.units ?? []
              const open_ = expanded.has(run.id)
              return (
                <Fragment key={run.id}>
                <TableRow hover sx={{ cursor: 'pointer', '& > td': { borderBottom: open_ ? 'none' : undefined } }} onClick={() => open(run.id)}>
                  <TableCell padding="checkbox">
                    {units.length > 0 && (
                      <IconButton
                        size="small"
                        aria-label={`${open_ ? 'Hide' : 'Show'} units of the run created ${formatInstant(run.createdAt)}`}
                        aria-expanded={open_}
                        onClick={(event) => {
                          event.stopPropagation()
                          toggle(run.id)
                        }}
                      >
                        {open_ ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                      </IconButton>
                    )}
                  </TableCell>
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
                    <StepBadges steps={run.steps} replay={run.trigger === 'RETRY'} />
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
                      {units.length > 1 && (
                        <Typography variant="caption" color="text.secondary">
                          {unitsSummary(units)}
                        </Typography>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
                {units.length > 0 && (
                  <TableRow>
                    <TableCell colSpan={8} sx={{ py: 0, border: open_ ? undefined : 'none' }}>
                      <Collapse in={open_} timeout="auto" unmountOnExit>
                        <Box sx={{ py: 1, pl: 2 }}>
                          <RunUnitsList units={units} now={now} />
                        </Box>
                      </Collapse>
                    </TableCell>
                  </TableRow>
                )}
                </Fragment>
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
