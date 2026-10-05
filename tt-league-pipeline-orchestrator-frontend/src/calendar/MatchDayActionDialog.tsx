import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { MatchDayDetail, TrackedMatch } from '../api/types'
import { useApi } from '../api/useApi'

export const MAX_NOTE_LENGTH = 2000

export type MatchDayAction =
  | { readonly kind: 'close' }
  | { readonly kind: 'reopen' }
  | { readonly kind: 'ignore'; readonly match: TrackedMatch }
  | { readonly kind: 'unignore'; readonly match: TrackedMatch }
  /** `matchId` preselects what the note applies to; null is the match day itself. */
  | { readonly kind: 'note'; readonly matchId: string | null }

interface MatchDayActionDialogProps {
  readonly matchDayId: string
  readonly action: MatchDayAction
  readonly matches: readonly TrackedMatch[]
  /** The action succeeded; the dialog closes after this call. */
  readonly onDone: (detail: MatchDayDetail) => void
  /** The server rejected the action because the match day changed or is in another state; reload it. */
  readonly onConflict: () => void
  readonly onClose: () => void
}

const TITLES: Readonly<Record<MatchDayAction['kind'], string>> = {
  close: 'Close match day',
  reopen: 'Reopen match day',
  ignore: 'Ignore match',
  unignore: 'Stop ignoring match',
  note: 'Add note',
}

const SUBMIT_LABELS: Readonly<Record<MatchDayAction['kind'], string>> = {
  close: 'Close match day',
  reopen: 'Reopen match day',
  ignore: 'Ignore match',
  unignore: 'Stop ignoring',
  note: 'Add note',
}

const DAY = 'match-day'

function matchLabel(match: TrackedMatch): string {
  return `${match.homeTeamName} – ${match.awayTeamName}`
}

/**
 * One dialog for the five operator actions. Nothing is retried: a 409 shows the server message and asks the page to
 * reload, a 400 on the text or note is shown on the field, and every other failure is shown as an alert.
 */
export function MatchDayActionDialog({
  matchDayId,
  action,
  matches,
  onDone,
  onConflict,
  onClose,
}: MatchDayActionDialogProps) {
  const api = useApi()
  const isNote = action.kind === 'note'
  const [text, setText] = useState('')
  const [target, setTarget] = useState(action.kind === 'note' ? (action.matchId ?? DAY) : DAY)
  const [pending, setPending] = useState(false)
  const [showErrors, setShowErrors] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [alert, setAlert] = useState<string | null>(null)

  const trimmed = text.trim()
  const clientError = !showErrors
    ? null
    : isNote && trimmed === ''
      ? 'A note needs text'
      : text.length > MAX_NOTE_LENGTH
        ? `At most ${MAX_NOTE_LENGTH} characters`
        : null
  const error = clientError ?? fieldError

  const send = async (): Promise<MatchDayDetail> => {
    const note = trimmed === '' ? undefined : trimmed
    switch (action.kind) {
      case 'close':
        return api.matchDays.closeMatchDay(matchDayId, note)
      case 'reopen':
        return api.matchDays.reopenMatchDay(matchDayId, note)
      case 'ignore':
        return api.matchDays.ignoreMatch(matchDayId, action.match.matchId, note)
      case 'unignore':
        return api.matchDays.unignoreMatch(matchDayId, action.match.matchId, note)
      case 'note':
        return api.matchDays.addMatchDayNote(matchDayId, trimmed, target === DAY ? undefined : target)
    }
  }

  const submit = async () => {
    setShowErrors(true)
    setAlert(null)
    setFieldError(null)
    if ((isNote && trimmed === '') || text.length > MAX_NOTE_LENGTH) {
      return
    }
    setPending(true)
    try {
      const detail = await send()
      onDone(detail)
      onClose()
    } catch (failure: unknown) {
      setPending(false)
      if (!(failure instanceof ApiError)) {
        setAlert(failure instanceof Error ? failure.message : 'The action could not be completed')
        return
      }
      if (failure.status === 409) {
        setAlert(failure.message)
        onConflict()
      } else if (failure.status === 400 && (failure.problem?.field === 'text' || failure.problem?.field === 'note')) {
        setFieldError(failure.message)
      } else {
        setAlert(failure.message)
      }
    }
  }

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby="match-day-action-title"
      onClose={() => {
        if (!pending) {
          onClose()
        }
      }}
    >
      <DialogTitle id="match-day-action-title">{TITLES[action.kind]}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          {alert !== null && <Alert severity="error">{alert}</Alert>}
          {(action.kind === 'ignore' || action.kind === 'unignore') && (
            <Box>{matchLabel(action.match)}</Box>
          )}
          {isNote && (
            <TextField
              select
              size="small"
              label="Applies to"
              value={target}
              disabled={pending}
              onChange={(event) => setTarget(event.target.value)}
            >
              <MenuItem value={DAY}>The match day</MenuItem>
              {matches.map((match) => (
                <MenuItem key={match.matchId} value={match.matchId}>
                  {matchLabel(match)}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label={isNote ? 'Note' : 'Note (optional)'}
            multiline
            minRows={3}
            required={isNote}
            value={text}
            disabled={pending}
            error={error !== null}
            helperText={error ?? `${text.length} / ${MAX_NOTE_LENGTH}`}
            onChange={(event) => {
              setText(event.target.value)
              setFieldError(null)
            }}
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
          {SUBMIT_LABELS[action.kind]}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
