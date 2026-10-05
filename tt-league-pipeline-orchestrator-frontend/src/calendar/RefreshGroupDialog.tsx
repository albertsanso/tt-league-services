import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { ApiError, triggerResults } from '../api/ApiError'
import type { TriggerRunOutcome } from '../api/runs'
import type { PipelineSource, TriggerResult } from '../api/types'
import { useApi } from '../api/useApi'
import { TriggerResultList } from '../runs/TriggerResultList'

interface RefreshGroupDialogProps {
  readonly matchDayId: string
  readonly source: PipelineSource
  /** Called for a 201 or 202 answer; the dialog does not close itself on failures. */
  readonly onRefreshed: (outcome: TriggerRunOutcome) => void
  readonly onClose: () => void
}

interface Rejection {
  readonly title: string
  readonly results: readonly TriggerResult[]
}

/** The refresh only creates a run through the orchestrator; the browser never builds ingest filters. */
export function RefreshGroupDialog({ matchDayId, source, onRefreshed, onClose }: RefreshGroupDialogProps) {
  const api = useApi()
  const [force, setForce] = useState(false)
  const [pending, setPending] = useState(false)
  const [alert, setAlert] = useState<string | null>(null)
  const [rejection, setRejection] = useState<Rejection | null>(null)

  const submit = async () => {
    setAlert(null)
    setRejection(null)
    setPending(true)
    try {
      const outcome = await api.matchDays.refreshMatchDay(matchDayId, force)
      onRefreshed(outcome)
      onClose()
    } catch (failure: unknown) {
      setPending(false)
      if (failure instanceof ApiError && (failure.status === 409 || failure.status === 422)) {
        setRejection({ title: failure.message, results: triggerResults(failure) })
      } else {
        setAlert(failure instanceof Error ? failure.message : 'The refresh could not be started')
      }
    }
  }

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="refresh-group-title"
      onClose={() => {
        if (!pending) {
          onClose()
        }
      }}
    >
      <DialogTitle id="refresh-group-title">Refresh group</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          {alert !== null && <Alert severity="error">{alert}</Alert>}
          {rejection !== null && (
            <Alert severity="error">
              <AlertTitle>{rejection.title}</AlertTitle>
              <TriggerResultList results={rejection.results} onNavigate={onClose} />
            </Alert>
          )}
          <Typography>
            {source === 'RFETM'
              ? 'Starts a run that ingests the whole category of this match day (RFETM scopes only carry the category).'
              : 'Starts a run that ingests this round of the group of this match day.'}
          </Typography>
          <FormControlLabel
            control={<Checkbox checked={force} disabled={pending} onChange={(event) => setForce(event.target.checked)} />}
            label="Ignore the ingest no-change check"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={pending}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={pending}
          onClick={() => void submit()}
          startIcon={pending ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Start refresh
        </Button>
      </DialogActions>
    </Dialog>
  )
}
