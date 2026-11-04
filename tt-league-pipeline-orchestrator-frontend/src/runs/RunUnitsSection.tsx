import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Accordion from '@mui/material/Accordion'
import AccordionDetails from '@mui/material/AccordionDetails'
import AccordionSummary from '@mui/material/AccordionSummary'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import type { RunDetail, RunUnitDetail } from '../api/types'
import { useApi } from '../api/useApi'
import { Can } from '../auth/Can'
import { ArtifactsTable } from './ArtifactsTable'
import { CopyValue } from './CopyValue'
import { packageFilename, saveBlob } from './download'
import { elapsedMs, formatDuration, formatInstant } from './format'
import { ImportReportView } from './ImportReportView'
import { RetryUnitDialog } from './RetryUnitDialog'
import { unitRetryBlockedLabel } from './retry'
import { isRunningUnitStatus, isTerminalUnitStatus } from './runStatus'
import { StepsTable } from './StepsTable'
import { UnitProgressBar } from './UnitProgressBar'
import { UnitStatusChip } from './UnitStatusChip'

/** Failed, skipped and running units open by default; so does the only unit of a run. */
function openByDefault(unit: RunUnitDetail, unitCount: number): boolean {
  return unitCount === 1 || unit.status === 'FAILED' || unit.status === 'SKIPPED' || isRunningUnitStatus(unit.status)
}

interface UnitPanelProps {
  readonly run: RunDetail
  readonly unit: RunUnitDetail
  readonly now: number
  readonly expanded: boolean
  readonly onToggle: () => void
  readonly onRetry: () => void
}

function UnitPanel({ run, unit, now, expanded, onToggle, onRetry }: UnitPanelProps) {
  const api = useApi()
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const running = isRunningUnitStatus(unit.status)
  const duration = running ? elapsedMs(unit.startedAt, unit.finishedAt, now) : unit.durationMs

  const download = async () => {
    setDownloadError(null)
    try {
      const { blob, filename } = await api.runs.downloadUnitPackage(run.id, unit.id)
      saveBlob(blob, packageFilename(filename, run.id, unit.id))
    } catch (failure: unknown) {
      setDownloadError(failure instanceof Error ? failure.message : 'The package could not be downloaded')
    }
  }

  const retryButton = (
    <Button variant="outlined" size="small" onClick={onRetry} disabled={!unit.retry.eligible}>
      Retry unit
    </Button>
  )

  return (
    <Accordion expanded={expanded} onChange={onToggle} disableGutters slotProps={{ transition: { unmountOnExit: true } }}>
      <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-label={`Unit ${unit.label}`}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', width: '100%' }}>
          <UnitStatusChip status={unit.status} />
          <Typography>{unit.label}</Typography>
          <Typography variant="caption" color="text.secondary">
            {formatDuration(duration)}
          </Typography>
          {unit.error !== null && (
            <Typography variant="caption" color="text.secondary">
              {unit.error.code}
            </Typography>
          )}
          {unit.progress !== null && !expanded && <UnitProgressBar progress={unit.progress} compact />}
        </Box>
      </AccordionSummary>
      <AccordionDetails>
        <Typography variant="body2" color="text.secondary">
          Started {formatInstant(unit.startedAt)} · Finished {formatInstant(unit.finishedAt)} · Duration{' '}
          {formatDuration(duration)}
        </Typography>
        {unit.error !== null && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {unit.error.code}: {unit.error.message}
          </Alert>
        )}
        {unit.progress !== null && (
          <Box sx={{ mt: 1 }}>
            <UnitProgressBar progress={unit.progress} />
          </Box>
        )}
        <Box sx={{ mt: 1 }}>
          <CopyValue label="Unit key" value={unit.unitKey} />
          {unit.ingestRunId !== null && <CopyValue label="Ingest run" value={unit.ingestRunId} />}
          {unit.importJobId !== null && <CopyValue label="Import job" value={unit.importJobId} />}
          {unit.storageFolder !== null && <CopyValue label="Storage folder" value={unit.storageFolder} />}
        </Box>
        <Box sx={{ mt: 1, display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          {unit.packageUrl !== null && (
            <Button variant="outlined" size="small" onClick={() => void download()}>
              Download package
            </Button>
          )}
          <Can capability="trigger-runs" mode="hide">
            {unit.retry.eligible ? (
              retryButton
            ) : (
              <Tooltip title={unitRetryBlockedLabel(unit.retry.reason)}>
                <span>{retryButton}</span>
              </Tooltip>
            )}
          </Can>
          {unit.retriedBy.length > 0 && (
            <Typography variant="body2" component="span">
              Retried by{' '}
              {unit.retriedBy.map((retry, index) => (
                <span key={retry.runId}>
                  {index > 0 && ', '}
                  <Link component={RouterLink} to={`/runs/${retry.runId}`}>
                    {retry.runId}
                  </Link>{' '}
                  ({retry.status})
                </span>
              ))}
            </Typography>
          )}
        </Box>
        {downloadError !== null && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {downloadError}
          </Alert>
        )}

        <Typography variant="subtitle2" sx={{ mt: 2 }} gutterBottom>
          Steps
        </Typography>
        {unit.steps.length === 0 ? (
          <Typography color="text.secondary">No steps.</Typography>
        ) : (
          <StepsTable steps={unit.steps} now={now} label={`Steps of ${unit.label}`} />
        )}

        <Typography variant="subtitle2" sx={{ mt: 2 }} gutterBottom>
          Artifacts
        </Typography>
        {unit.artifacts.length === 0 ? (
          <Typography color="text.secondary">No artifacts.</Typography>
        ) : (
          <ArtifactsTable artifacts={unit.artifacts} label={`Artifacts of ${unit.label}`} />
        )}

        <Typography variant="subtitle2" sx={{ mt: 2 }} gutterBottom>
          Import counters
        </Typography>
        <ImportReportView report={unit.counters} active={!isTerminalUnitStatus(unit.status)} />
      </AccordionDetails>
    </Accordion>
  )
}

/** One accordion per unit of the run, with the unit's own status, progress, steps, artifacts and retry. */
export function RunUnitsSection({ run, now }: { run: RunDetail; now: number }) {
  const [overrides, setOverrides] = useState<Readonly<Record<string, boolean>>>({})
  const [retrying, setRetrying] = useState<RunUnitDetail | null>(null)
  const navigate = useNavigate()

  if (run.units.length === 0) {
    return <Typography color="text.secondary">No units yet.</Typography>
  }
  return (
    <>
      {run.units.map((unit) => (
        <UnitPanel
          key={unit.id}
          run={run}
          unit={unit}
          now={now}
          expanded={overrides[unit.id] ?? openByDefault(unit, run.units.length)}
          onToggle={() =>
            setOverrides((previous) => ({
              ...previous,
              [unit.id]: !(previous[unit.id] ?? openByDefault(unit, run.units.length)),
            }))
          }
          onRetry={() => setRetrying(unit)}
        />
      ))}
      {retrying !== null && (
        <RetryUnitDialog runId={run.id} unit={retrying} onClose={() => setRetrying(null)} onRetried={(runId) => navigate(`/runs/${runId}`)} />
      )}
    </>
  )
}
