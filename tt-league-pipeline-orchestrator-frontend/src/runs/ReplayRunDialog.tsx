import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import { useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { RunSummary } from '../api/types'
import { useApi } from '../api/useApi'

interface ReplayRunDialogProps {
  readonly runId: string
  readonly onClose: () => void
  /** Called with the new RETRY run once the server created it. */
  readonly onReplayed: (run: RunSummary) => void
}

export function ReplayRunDialog({ runId, onClose, onReplayed }: ReplayRunDialogProps) {
  const api = useApi()
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const submit = async () => {
    setSubmitting(true)
    setMessage(null)
    try {
      onReplayed(await api.runs.replayRun(runId))
    } catch (failure: unknown) {
      setSubmitting(false)
      setMessage(failure instanceof ApiError || failure instanceof Error ? failure.message : 'The replay could not be started')
    }
  }

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="replay-run-title"
      onClose={() => {
        if (!submitting) {
          onClose()
        }
      }}
    >
      <DialogTitle id="replay-run-title">Replay import</DialogTitle>
      <DialogContent>
        {message !== null && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {message}
          </Alert>
        )}
        <DialogContentText component="div">
          <p>Ingest is skipped: the stored ZIP of this run is submitted to the platform again.</p>
          <p>
            If the platform already imported the same content successfully, it returns the existing import job and nothing is
            re-imported.
          </p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={() => void submit()} disabled={submitting}>
          Replay import
        </Button>
      </DialogActions>
    </Dialog>
  )
}
