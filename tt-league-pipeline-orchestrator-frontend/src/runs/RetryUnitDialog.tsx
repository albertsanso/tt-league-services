import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import { useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { RunUnit } from '../api/types'
import { useApi } from '../api/useApi'

interface RetryUnitDialogProps {
  readonly runId: string
  readonly unit: Pick<RunUnit, 'id' | 'label'>
  readonly onClose: () => void
  /** Called with the id of the UNIT_RETRY run once the server created it. */
  readonly onRetried: (runId: string) => void
}

/** Confirms re-running one unit: ingest, package and import start again for its scope as a new run. */
export function RetryUnitDialog({ runId, unit, onClose, onRetried }: RetryUnitDialogProps) {
  const api = useApi()
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const submit = async () => {
    setSubmitting(true)
    setMessage(null)
    try {
      onRetried((await api.runs.retryUnit(runId, unit.id)).runId)
    } catch (failure: unknown) {
      setSubmitting(false)
      setMessage(
        failure instanceof ApiError || failure instanceof Error ? failure.message : 'The retry could not be started',
      )
    }
  }

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="retry-unit-title"
      onClose={() => {
        if (!submitting) {
          onClose()
        }
      }}
    >
      <DialogTitle id="retry-unit-title">Retry unit</DialogTitle>
      <DialogContent>
        {message !== null && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {message}
          </Alert>
        )}
        <DialogContentText component="div">
          <p>
            Ingest, package and import run again for <strong>{unit.label}</strong> only, as a new run. This run and its
            other units stay as they are.
          </p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={() => void submit()} disabled={submitting}>
          Retry unit
        </Button>
      </DialogActions>
    </Dialog>
  )
}
