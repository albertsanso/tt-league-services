import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router-dom'
import type { RunDetail } from '../api/types'
import { Can } from '../auth/Can'
import { ArtifactsTable } from '../runs/ArtifactsTable'
import { CopyValue } from '../runs/CopyValue'
import { elapsedMs, formatDuration, formatInstant, scopeDetails } from '../runs/format'
import { ImportReportView } from '../runs/ImportReportView'
import { REUSED_IMPORT_LABEL, replayBlockedLabel } from '../runs/replay'
import { ReplayRunDialog } from '../runs/ReplayRunDialog'
import { RunActivityLog } from '../runs/RunActivityLog'
import { RunStatusChip } from '../runs/RunStatusChip'
import { RunUnitsSection } from '../runs/RunUnitsSection'
import { isActiveStatus, TRIGGER_LABELS } from '../runs/runStatus'
import { StepsTable } from '../runs/StepsTable'
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

/** Enabled only when the server says the run can be replayed; the reason for a refusal is a label of its code. */
function ReplayButton({ run, onReplay }: { run: RunDetail; onReplay: () => void; disabled?: boolean }) {
  const button = (
    <Button variant="outlined" onClick={onReplay} disabled={!run.replay.allowed}>
      Replay import
    </Button>
  )
  return run.replay.allowed ? (
    button
  ) : (
    <Tooltip title={replayBlockedLabel(run.replay.code)}>
      <span>{button}</span>
    </Tooltip>
  )
}

function Header({ run, now, onReplay }: { run: RunDetail; now: number; onReplay: () => void }) {
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
        {run.importJobReused === true && <Chip size="small" color="info" label={REUSED_IMPORT_LABEL} />}
        <Box sx={{ flexGrow: 1 }} />
        <Can capability="trigger-runs" mode="hide">
          <ReplayButton run={run} onReplay={onReplay} />
        </Can>
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
            {run.retryOfUnitId !== null ? 'Retry of a unit of' : 'Replay of'}{' '}
            <Link component={RouterLink} to={`/runs/${run.retryOfRunId}`}>
              {run.retryOfRunId}
            </Link>
          </Box>
        )}
        {run.ingestRunId !== null && <CopyValue label="Ingest run" value={run.ingestRunId} />}
        {run.importJobId !== null && <CopyValue label="Import job" value={run.importJobId} />}
      </Box>
    </>
  )
}

/** The run detail payload as the API sent it, pretty-printed, with a copy button. */
function RunJson({ run }: { run: RunDetail }) {
  const text = JSON.stringify(run, null, 2)
  return (
    <Box sx={{ mt: 2 }}>
      <Button variant="outlined" size="small" onClick={() => void navigator.clipboard?.writeText(text)}>
        Copy JSON
      </Button>
      <Box
        component="pre"
        aria-label="Run JSON"
        sx={{ mt: 1, p: 2, overflow: 'auto', maxHeight: '70vh', bgcolor: 'action.hover', borderRadius: 1, fontSize: 13 }}
      >
        {text}
      </Box>
    </Box>
  )
}

export default function RunDetailPage() {
  const { runId = '' } = useParams()
  const location = useLocation()
  const backTo = (location.state as { backTo?: string } | null)?.backTo ?? ''
  const { run, loading, error, notFound, refetch } = useRunDetail(runId)
  const navigate = useNavigate()
  const [replayOpen, setReplayOpen] = useState(false)
  const [tab, setTab] = useState<'overview' | 'json'>('overview')
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
      <Header run={run} now={now} onReplay={() => setReplayOpen(true)} />
      {replayOpen && (
        <ReplayRunDialog
          runId={run.id}
          onClose={() => setReplayOpen(false)}
          onReplayed={(replayed) => navigate(`/runs/${replayed.id}`)}
        />
      )}

      <Tabs value={tab} onChange={(_event, value: 'overview' | 'json') => setTab(value)} sx={{ mt: 2 }} aria-label="Run views">
        <Tab label="Overview" value="overview" />
        <Tab label="JSON" value="json" />
      </Tabs>

      {tab === 'json' && <RunJson run={run} />}
      {tab === 'overview' && (
        <>
      <Section title="Scope">
        {scopeDetails(run).map((line) => (
          <Typography key={line}>{line}</Typography>
        ))}
      </Section>

      <Section title="Units">
        <RunUnitsSection run={run} now={now} />
      </Section>

      <Section title="Steps">
        {run.steps.length === 0 ? (
          <Typography color="text.secondary">No steps yet.</Typography>
        ) : (
          <StepsTable steps={run.steps} now={now} label="Steps" />
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
          <ArtifactsTable artifacts={run.artifacts} label="Artifacts" />
        )}
      </Section>

      <Section title="Import report">
        {run.importJobReused === true && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {REUSED_IMPORT_LABEL}
          </Alert>
        )}
        <ImportReportView report={run.importReport} active={active} />
      </Section>

      <Section title="Live activity">
        <RunActivityLog runId={run.id} />
      </Section>
        </>
      )}
    </>
  )
}
