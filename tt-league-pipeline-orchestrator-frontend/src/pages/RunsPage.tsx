import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Collapse from '@mui/material/Collapse'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useMemo, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import type { TriggerRunOutcome } from '../api/runs'
import { Can } from '../auth/Can'
import { RunActivityLog } from '../runs/RunActivityLog'
import { RunFilterBar } from '../runs/RunFilterBar'
import { RunNowDialog } from '../runs/RunNowDialog'
import { RunsTable } from '../runs/RunsTable'
import { hasActiveFilters, hasRangeError, parseRunFilters, serializeRunFilters, toRunListQuery } from '../runs/runFilters'
import type { RunFilters } from '../runs/runFilters'
import { isActiveStatus } from '../runs/runStatus'
import { useRunList } from '../runs/useRunList'

function TriggerSummary({ outcome, onDismiss }: { outcome: TriggerRunOutcome; onDismiss: () => void }) {
  const { results } = outcome.response
  const allCreated = results.every((result) => result.outcome === 'CREATED')
  const anyCreated = results.some((result) => result.outcome === 'CREATED')
  const severity = allCreated ? 'success' : anyCreated ? 'warning' : 'info'
  return (
    <Alert severity={severity} onClose={onDismiss} sx={{ mb: 2 }}>
      <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
        {results.map((result) => (
          <li key={result.source}>
            {result.source} —{' '}
            {result.outcome === 'CREATED' && result.run !== undefined ? (
              <>
                run created.{' '}
                <Link component={RouterLink} to={`/runs/${result.run.id}`}>
                  Open run
                </Link>
              </>
            ) : result.outcome === 'QUEUED' ? (
              'trigger queued; it starts when the active run finishes.'
            ) : (
              (result.message ?? result.outcome.toLowerCase())
            )}
          </li>
        ))}
      </ul>
    </Alert>
  )
}

export default function RunsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => parseRunFilters(searchParams), [searchParams])
  const rangeError = hasRangeError(filters)
  const query = useMemo(() => (rangeError ? null : toRunListQuery(filters)), [filters, rangeError])
  const { page, loading, error, newRunsAvailable, refetch } = useRunList(query)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [triggered, setTriggered] = useState<TriggerRunOutcome | null>(null)
  const [logOpen, setLogOpen] = useState<boolean | null>(null)

  const hasActiveRun = page?.items.some((run) => isActiveStatus(run.status)) ?? false
  const logExpanded = logOpen ?? hasActiveRun

  const write = (next: Omit<RunFilters, 'errors'>, replace: boolean) =>
    setSearchParams(serializeRunFilters(next), { replace })
  const changeFilters = (
    patch: Partial<Pick<RunFilters, 'sources' | 'statuses' | 'unitKey' | 'fromDate' | 'toDate'>>,
    replace: boolean,
  ) => write({ ...filters, ...patch, page: 1 }, replace)
  const clearFilters = () =>
    write({ sources: [], statuses: [], unitKey: null, fromDate: null, toDate: null, page: 1 }, false)

  const seasons = useMemo(
    () => [...new Set(page?.items.map((run) => run.season) ?? [])].sort().reverse(),
    [page],
  )

  const handleTriggered = (outcome: TriggerRunOutcome) => {
    setDialogOpen(false)
    setTriggered(outcome)
    if (filters.page === 1 && !rangeError) {
      refetch()
    }
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Runs
        </Typography>
        <Can capability="trigger-runs" mode="disable">
          <Button variant="contained" onClick={() => setDialogOpen(true)}>
            Run now
          </Button>
        </Can>
      </Box>

      {dialogOpen && (
        <RunNowDialog seasonSuggestions={seasons} onClose={() => setDialogOpen(false)} onTriggered={handleTriggered} />
      )}
      {triggered !== null && <TriggerSummary outcome={triggered} onDismiss={() => setTriggered(null)} />}
      {filters.errors.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {filters.errors.map((message) => (
            <div key={message}>{message}</div>
          ))}
        </Alert>
      )}

      <RunFilterBar filters={filters} onChange={changeFilters} onClear={clearFilters} />

      <Box sx={{ mb: 2 }}>
        <Button size="small" onClick={() => setLogOpen(!logExpanded)} aria-expanded={logExpanded}>
          Live activity
        </Button>
        <Collapse in={logExpanded} mountOnEnter={false}>
          <RunActivityLog />
        </Collapse>
      </Box>

      {error !== null && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={refetch}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}
      {newRunsAvailable && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => write({ ...filters, page: 1 }, false)}>
              Go to first page
            </Button>
          }
        >
          New runs available
        </Alert>
      )}
      {loading && <LinearProgress aria-label="Loading runs" sx={{ mb: 1 }} />}
      {page !== null && (
        <RunsTable
          page={page}
          filtered={hasActiveFilters(filters)}
          onPageChange={(next) => write({ ...filters, page: next + 1 }, false)}
          onClearFilters={clearFilters}
        />
      )}
    </>
  )
}
