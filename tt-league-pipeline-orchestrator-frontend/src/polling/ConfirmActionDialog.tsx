import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/ApiError'

interface ConfirmActionDialogProps {
  readonly title: string
  readonly confirmLabel: string
  readonly children: ReactNode
  /** Calls the endpoint; a rejected promise is shown in the dialog. */
  readonly action: () => Promise<unknown>
  /** The action succeeded; the dialog closes after this call. */
  readonly onDone: () => void
  /** The server answered 404 or 409 (the target changed or is gone): the page should reload what it shows. */
  readonly onConflict: () => void
  readonly onClose: () => void
}

/** A confirmation for one polling action. Nothing is retried: the server's answer is shown as it is. */
export function ConfirmActionDialog({
  title,
  confirmLabel,
  children,
  action,
  onDone,
  onConflict,
  onClose,
}: ConfirmActionDialogProps) {
  const [pending, setPending] = useState(false)
  const [alert, setAlert] = useState<string | null>(null)

  const submit = async () => {
    setPending(true)
    setAlert(null)
    try {
      await action()
      onDone()
      onClose()
    } catch (failure: unknown) {
      setPending(false)
      setAlert(failure instanceof Error ? failure.message : 'The action could not be completed')
      if (failure instanceof ApiError && (failure.status === 404 || failure.status === 409)) {
        onConflict()
      }
    }
  }

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="confirm-action-title"
      onClose={() => {
        if (!pending) {
          onClose()
        }
      }}
    >
      <DialogTitle id="confirm-action-title">{title}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          {alert !== null && <Alert severity="error">{alert}</Alert>}
          {children}
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
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
