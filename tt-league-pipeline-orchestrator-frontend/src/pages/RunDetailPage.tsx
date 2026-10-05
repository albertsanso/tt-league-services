import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom'
import type { ImportReport, RunDetail } from '../api/types'
import { elapsedMs, formatBytes, formatDuration, formatInstant, scopeDetails } from '../runs/format'
import { RunActivityLog } from '../runs/RunActivityLog'
import { RunStatusChip } from '../runs/RunStatusChip'
import { isActiveStatus, STEP_LABELS, STEP_STATUS_LABELS, stepStatusColor, TRIGGER_LABELS } from '../runs/runStatus'
import { useNow } from '../runs/useNow'
import { useRunDetail } from '../runs/useRunDetail'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box component="section" sx={{ mt: 3 }}>
      <Typography variant="h6" component="h3" gutterBottom>
        {title}
      </Typography>
      {children}
    </Box>
  )
}

function CopyId({ label, value }: { label: string; value: string }) {
  return (
    <Box component="span" sx={{ mr: 2, whiteSpace: 'nowrap' }}>
      {label}: <code>{value}</code>
      <Tooltip title="Copy">
        <IconButton
          size="small"
          aria-label={`Copy ${label}`}
          onClick={() => void navigator.clipboard?.writeText(value)}
        >
          <ContentCopyIcon fontSize="inherit" />
        </IconButton>
      </Tooltip>
    </Box>
  )
}

const REPORT_COUNTERS: ReadonlyArray<readonly [keyof ImportReport, string]> = [
  ['filesSeen', 'Files seen'],
  ['itemsPersisted', 'Items persisted'],
  ['skipped', 'Skipped'],
  ['processorFailures', 'Processor failures'],
  ['scheduledCreated', 'Scheduled created'],
  ['upgradedToPlayed', 'Upgraded to played'],
  ['rescheduled', 'Rescheduled'],
  ['partialActas', 'Partial actas'],
  ['invalidActas', 'Invalid actas'],
  ['unresolvedPendingFixtures', 'Unresolved pending fixtures'],
]

function ImportReportView({ report, active }: { report: ImportReport | null; active: boolean }) {
  if (report === null) {
    return <Typography color="text.secondary">{active ? 'No import report yet.' : 'No import report.'}</Typography>
  }
  return (
    <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 1, m: 0 }}>
      <Box>
        <Typography component="dt" variant="caption" color="text.secondary">
          Status
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {report.status}
        </Typography>
      </Box>
      <Box>
        <Typography component="dt" variant="caption" color="text.secondary">
          Received
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {formatInstant(report.receivedAt)}
        </Typography>
      </Box>
      {REPORT_COUNTERS.map(([field, label]) => (
        <Box key={field}>
          <Typography component="dt" variant="caption" color="text.secondary">
            {label}
          </Typography>
          <Typography component="dd" sx={{ m: 0 }}>
            {String(report[field])}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}

function Header({ run, now }: { run: RunDetail; now: number }) {
  const active = isActiveStatus(run.status)
  const duration = active ? elapsedMs(run.startedAt, run.finishedAt, now) : (run.durationMs ?? elapsedMs(run.startedAt, run.finishedAt, now))
  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="h5" component="h2">
          Run details
        </Typography>
        <RunStatusChip status={run.status} />
        {run.force && <Chip size="small" variant="outlined" label="forced" />}
      </Box>
      <Typography sx={{ mt: 1 }}>
        {run.source} · {run.season} · {TRIGGER_LABELS[run.trigger]}
        {run.requestedBy ? ` by ${run.requestedBy}` : ''}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Created {formatInstant(run.createdAt)} · Started {formatInstant(run.startedAt)} · Finished{' '}
        {formatInstant(run.finishedAt)} · Duration {formatDuration(duration)}
      </Typography>
      {run.error !== null && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {run.error.code}: {run.error.message}
        </Alert>
      )}
      <Box sx={{ mt: 1 }}>
        {run.retryOfRunId !== null && (
          <Box component="span" sx={{ mr: 2 }}>
            Retry of{' '}
            <Link component={RouterLink} to={`/runs/${run.retryOfRunId}`}>
              {run.retryOfRunId}
            </Link>
          </Box>
        )}
        {run.ingestRunId !== null && <CopyId label="Ingest run" value={run.ingestRunId} />}
        {run.importJobId !== null && <CopyId label="Import job" value={run.importJobId} />}
      </Box>
    </>
  )
}

export default function RunDetailPage() {
  const { runId = '' } = useParams()
  const location = useLocation()
  const backTo = (location.state as { backTo?: string } | null)?.backTo ?? ''
  const { run, loading, error, notFound, refetch } = useRunDetail(runId)
  const active = run !== null && isActiveStatus(run.status)
  const now = useNow(1000, active)

  const backLink = (
    <Link component={RouterLink} to={`/runs${backTo}`}>
      All runs
    </Link>
  )

  if (notFound) {
    return (
      <>
        <Typography variant="h5" component="h2" gutterBottom>
          Run not found
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 1 }}>
          There is no run with id {runId}.
        </Typography>
        {backLink}
      </>
    )
  }

  if (run === null) {
    return (
      <>
        <Box sx={{ mb: 2 }}>{backLink}</Box>
        <Typography variant="h5" component="h2" gutterBottom>
          Run details
        </Typography>
        {error !== null ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={refetch}>
                Retry
              </Button>
            }
          >
            {error}
          </Alert>
        ) : (
          loading && <Typography color="text.secondary">Loading…</Typography>
        )}
      </>
    )
  }

  return (
    <>
      <Box sx={{ mb: 2 }}>{backLink}</Box>
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
      <Header run={run} now={now} />

      <Section title="Scope">
        {scopeDetails(run).map((line) => (
          <Typography key={line}>{line}</Typography>
        ))}
      </Section>

      <Section title="Steps">
        {run.steps.length === 0 ? (
          <Typography color="text.secondary">No steps yet.</Typography>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Steps">
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
                  <TableCell>Retryable</TableCell>
                  <TableCell>Error</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {run.steps.map((step) => (
                  <TableRow key={`${step.kind}-${step.attempt}`}>
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
                    <TableCell>{step.retryable === null ? '—' : step.retryable ? 'Yes' : 'No'}</TableCell>
                    <TableCell>{step.error ? `${step.error.code}: ${step.error.message}` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Section>

      <Section title="Issues">
        {run.issues.length === 0 ? (
          <Typography color="text.secondary">No issues</Typography>
        ) : (
          <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
            {run.issues.map((issue) => (
              <li key={issue}>{issue}</li>
            ))}
          </ul>
        )}
      </Section>

      <Section title="Artifacts">
        {run.artifacts.length === 0 ? (
          <Typography color="text.secondary">No artifacts</Typography>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Artifacts">
              <TableHead>
                <TableRow>
                  <TableCell>Kind</TableCell>
                  <TableCell>Size</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell>SHA-256</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {run.artifacts.map((artifact) => (
                  <TableRow key={`${artifact.kind}-${artifact.sha256}`}>
                    <TableCell>{artifact.kind}</TableCell>
                    <TableCell>{formatBytes(artifact.sizeBytes)}</TableCell>
                    <TableCell>{formatInstant(artifact.createdAt)}</TableCell>
                    <TableCell>
                      <Tooltip title={artifact.sha256}>
                        <Box
                          component="code"
                          sx={{ display: 'inline-block', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', verticalAlign: 'bottom' }}
                        >
                          {artifact.sha256}
                        </Box>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Section>

      <Section title="Import report">
        <ImportReportView report={run.importReport} active={active} />
      </Section>

      <Section title="Live activity">
        <RunActivityLog runId={run.id} />
      </Section>
    </>
  )
}
