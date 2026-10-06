import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { PollingPolicy } from '../api/types'
import { useApi } from '../api/useApi'
import { formatIsoDuration } from './format'
import { COUNT_FIELDS, DURATION_FIELDS, FIELD_LABELS, toFormValues, toRequest, validatePolicyForm } from './policyForm'
import type { PolicyField, PolicyFormValues } from './policyForm'

interface PolicyEditDialogProps {
  readonly policy: PollingPolicy
  /** The policy was saved (or reloaded after a conflict); the page should reload what it shows. */
  readonly onChanged: () => void
  readonly onClose: () => void
}

const isCount = (field: PolicyField) => (COUNT_FIELDS as readonly string[]).includes(field)

/**
 * Edits the full policy of one source. The checks only mirror `PollingSettings`; a 400 shows the server's reason and a
 * `STALE_POLICY` 409 keeps the input and offers to load the current values (and their version) into the form.
 */
export function PolicyEditDialog({ policy, onChanged, onClose }: PolicyEditDialogProps) {
  const api = useApi()
  const [values, setValues] = useState<PolicyFormValues>(() => toFormValues(policy))
  const [version, setVersion] = useState(policy.version)
  const [showErrors, setShowErrors] = useState(false)
  const [pending, setPending] = useState(false)
  const [alert, setAlert] = useState<string | null>(null)
  const [stale, setStale] = useState(false)

  const errors = validatePolicyForm(values)
  const shown = showErrors ? errors : {}

  const save = async () => {
    setShowErrors(true)
    setAlert(null)
    if (Object.keys(errors).length > 0) {
      return
    }
    setPending(true)
    try {
      await api.polling.replacePollingPolicy(policy.source, toRequest(values, version))
      onChanged()
      onClose()
    } catch (failure: unknown) {
      setPending(false)
      setAlert(failure instanceof Error ? failure.message : 'The policy could not be saved')
      setStale(failure instanceof ApiError && failure.status === 409 && failure.problem?.code === 'STALE_POLICY')
    }
  }

  const reloadCurrent = async () => {
    setPending(true)
    try {
      const current = await api.polling.getPollingPolicy(policy.source)
      setValues(toFormValues(current))
      setVersion(current.version)
      setAlert(null)
      setStale(false)
      setShowErrors(false)
      onChanged()
    } catch (failure: unknown) {
      setAlert(failure instanceof Error ? failure.message : 'The current policy could not be loaded')
    } finally {
      setPending(false)
    }
  }

  const field = (name: PolicyField) => (
    <TextField
      key={name}
      size="small"
      label={FIELD_LABELS[name]}
      value={values[name]}
      disabled={pending}
      error={shown[name] !== undefined}
      helperText={
        shown[name] ?? (isCount(name) ? undefined : `= ${formatIsoDuration(values[name])}`)
      }
      slotProps={{ htmlInput: isCount(name) ? { inputMode: 'numeric' } : {} }}
      onChange={(event) => setValues((previous) => ({ ...previous, [name]: event.target.value }))}
    />
  )

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="policy-edit-title"
      onClose={() => {
        if (!pending) {
          onClose()
        }
      }}
    >
      <DialogTitle id="policy-edit-title">{`Edit polling policy of ${policy.source}`}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          {alert !== null && (
            <Alert
              severity="error"
              action={
                stale ? (
                  <Button color="inherit" size="small" disabled={pending} onClick={() => void reloadCurrent()}>
                    Reload current values
                  </Button>
                ) : undefined
              }
            >
              {alert}
            </Alert>
          )}
          <Alert severity="info">
            Durations are ISO-8601 (for example PT2H, PT90M or P7D). Saving replaces all ten settings of {policy.source}.
          </Alert>
          {DURATION_FIELDS.map(field)}
          {COUNT_FIELDS.map(field)}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={pending}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={pending}
          onClick={() => void save()}
          startIcon={pending ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Save policy
        </Button>
      </DialogActions>
    </Dialog>
  )
}
