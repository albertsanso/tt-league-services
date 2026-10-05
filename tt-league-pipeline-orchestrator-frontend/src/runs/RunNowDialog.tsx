import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Autocomplete from '@mui/material/Autocomplete'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControl from '@mui/material/FormControl'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormHelperText from '@mui/material/FormHelperText'
import FormLabel from '@mui/material/FormLabel'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import TextField from '@mui/material/TextField'
import DeleteIcon from '@mui/icons-material/Delete'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ApiError, triggerResults } from '../api/ApiError'
import type { TriggerResult, TriggerRunRequest } from '../api/types'
import type { TriggerRunOutcome } from '../api/runs'
import { useApi } from '../api/useApi'
import { emptyFilter, emptyRunNowForm, hasErrors, toTriggerRequest, validateRunNow } from './runNowForm'
import type { FilterDraft, RunNowErrors, RunNowForm } from './runNowForm'
import { SCOPE_TYPE_LABELS } from './runStatus'
import { SOURCES } from './runFilters'

interface RunNowDialogProps {
  readonly seasonSuggestions: readonly string[]
  readonly onClose: () => void
  /** Called for a 201 or 202 answer; the dialog does not close itself on failures. */
  readonly onTriggered: (outcome: TriggerRunOutcome) => void
}

interface Rejection {
  readonly title: string
  readonly results: readonly TriggerResult[]
}

const FILTER_FIELD_LABELS: ReadonlyArray<readonly [keyof FilterDraft, string]> = [
  ['category', 'Category'],
  ['group', 'Group'],
  ['phase', 'Phase'],
  ['territory', 'Territory'],
  ['gender', 'Gender'],
  ['matchDays', 'Match days'],
]

export function RunNowDialog({ seasonSuggestions, onClose, onTriggered }: RunNowDialogProps) {
  const api = useApi()
  const [form, setForm] = useState<RunNowForm>(emptyRunNowForm)
  const [showErrors, setShowErrors] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverErrors, setServerErrors] = useState<RunNowErrors>({})
  const [alert, setAlert] = useState<string | null>(null)
  const [rejection, setRejection] = useState<Rejection | null>(null)

  const clientErrors = showErrors ? validateRunNow(form) : {}
  const errors: RunNowErrors = { ...serverErrors, ...clientErrors }

  const update = (patch: Partial<RunNowForm>) => {
    setForm((previous) => ({ ...previous, ...patch }))
    setServerErrors({})
  }
  const updateFilter = (index: number, patch: Partial<FilterDraft>) => {
    update({ filters: form.filters.map((filter, position) => (position === index ? { ...filter, ...patch } : filter)) })
  }

  const submit = async () => {
    setShowErrors(true)
    setAlert(null)
    setRejection(null)
    setServerErrors({})
    if (hasErrors(validateRunNow(form))) {
      return
    }
    const request: TriggerRunRequest = toTriggerRequest(form)
    setSubmitting(true)
    try {
      const outcome = await api.runs.triggerRun(request)
      onTriggered(outcome)
    } catch (failure: unknown) {
      setSubmitting(false)
      if (!(failure instanceof ApiError)) {
        setAlert(failure instanceof Error ? failure.message : 'The run could not be started')
        return
      }
      if (failure.status === 409 || failure.status === 422) {
        setRejection({ title: failure.message, results: triggerResults(failure) })
      } else if (failure.status === 400 && failure.problem?.field !== undefined && isServerField(failure.problem.field)) {
        setServerErrors({ [failure.problem.field]: failure.message })
      } else {
        setAlert(failure.message)
      }
    }
  }

  const groupScope = form.scopeType === 'GROUP'

  return (
    <Dialog
      open
      fullWidth
      maxWidth="md"
      aria-labelledby="run-now-title"
      onClose={() => {
        if (!submitting) {
          onClose()
        }
      }}
    >
      <DialogTitle id="run-now-title">Run now</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          {alert !== null && <Alert severity="error">{alert}</Alert>}
          {rejection !== null && (
            <Alert severity="error">
              <AlertTitle>{rejection.title}</AlertTitle>
              <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
                {rejection.results.map((result) => (
                  <li key={result.source}>
                    {result.source} — {result.message ?? result.outcome}
                    {result.code !== undefined && ` (${result.code})`}
                    {result.activeRunId !== undefined && (
                      <>
                        {' '}
                        <Link component={RouterLink} to={`/runs/${result.activeRunId}`} onClick={onClose}>
                          Open active run
                        </Link>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            </Alert>
          )}

          <FormControl error={errors.source !== undefined} disabled={submitting}>
            <FormLabel id="run-now-source">Source</FormLabel>
            <RadioGroup
              row
              aria-labelledby="run-now-source"
              value={form.source}
              onChange={(event) => update({ source: event.target.value as RunNowForm['source'] })}
            >
              {SOURCES.map((source) => (
                <FormControlLabel key={source} value={source} control={<Radio />} label={source} />
              ))}
              <FormControlLabel value="ALL" control={<Radio />} label="All sources" />
            </RadioGroup>
            {errors.source !== undefined && <FormHelperText>{errors.source}</FormHelperText>}
          </FormControl>

          <Autocomplete
            freeSolo
            disabled={submitting}
            options={seasonSuggestions}
            inputValue={form.season}
            onInputChange={(_event, value) => update({ season: value })}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Season"
                placeholder="2025-2026"
                error={errors.season !== undefined}
                helperText={errors.season ?? 'Suggestions come from the runs already loaded'}
              />
            )}
          />

          <FormControl error={errors.scopeType !== undefined} disabled={submitting}>
            <FormLabel id="run-now-scope">Scope</FormLabel>
            <RadioGroup
              row
              aria-labelledby="run-now-scope"
              value={form.scopeType}
              onChange={(event) => update({ scopeType: event.target.value as RunNowForm['scopeType'] })}
            >
              <FormControlLabel value="OPEN_MATCH_DAYS" control={<Radio />} label={SCOPE_TYPE_LABELS.OPEN_MATCH_DAYS} />
              <FormControlLabel
                value="GROUP"
                control={<Radio />}
                label={SCOPE_TYPE_LABELS.GROUP}
                disabled={form.source === 'ALL'}
              />
              <FormControlLabel value="FULL_SEASON" control={<Radio />} label={SCOPE_TYPE_LABELS.FULL_SEASON} />
            </RadioGroup>
            <FormHelperText>
              {errors.scopeType ?? (form.source === 'ALL' ? 'Group scope needs exactly one source' : ' ')}
            </FormHelperText>
          </FormControl>

          {groupScope && (
            <Box role="group" aria-label="Group filters" sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              {form.filters.map((filter, index) => (
                <Box key={index} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start' }}>
                  {FILTER_FIELD_LABELS.map(([field, label]) => (
                    <TextField
                      key={field}
                      size="small"
                      label={`${label} ${index + 1}`}
                      value={filter[field]}
                      disabled={submitting}
                      error={errors.filterRows?.[index] !== undefined}
                      onChange={(event) => updateFilter(index, { [field]: event.target.value })}
                      sx={{ width: field === 'matchDays' ? 130 : 140 }}
                    />
                  ))}
                  <IconButton
                    aria-label={`Remove filter ${index + 1}`}
                    disabled={submitting || form.filters.length === 1}
                    onClick={() => update({ filters: form.filters.filter((_, position) => position !== index) })}
                  >
                    <DeleteIcon />
                  </IconButton>
                  {errors.filterRows?.[index] !== undefined && (
                    <FormHelperText error sx={{ width: '100%', mt: 0 }}>
                      Filter {index + 1}: {errors.filterRows[index]}
                    </FormHelperText>
                  )}
                </Box>
              ))}
              {errors.filters !== undefined && <FormHelperText error>{errors.filters}</FormHelperText>}
              <Box>
                <Button size="small" disabled={submitting} onClick={() => update({ filters: [...form.filters, emptyFilter()] })}>
                  Add filter
                </Button>
              </Box>
            </Box>
          )}

          <FormControlLabel
            control={
              <Checkbox
                checked={form.force}
                disabled={submitting}
                onChange={(event) => update({ force: event.target.checked })}
              />
            }
            label="Ignore the ingest no-change check"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={submitting}
          startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Start run
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function isServerField(field: string): field is 'season' | 'filters' | 'scopeType' | 'source' {
  return field === 'season' || field === 'filters' || field === 'scopeType' || field === 'source'
}
