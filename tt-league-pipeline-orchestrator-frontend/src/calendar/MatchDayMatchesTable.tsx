import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { TrackedMatch, TrackedMatchStatus } from '../api/types'
import { Can } from '../auth/Can'
import { formatInstant } from '../runs/format'
import { MATCH_STATUS_LABELS } from './completion'
import { resultText } from './results'
import type { ResultsView } from './results'

const STATUS_COLORS: Readonly<Record<TrackedMatchStatus, 'default' | 'success' | 'warning' | 'error' | 'info'>> = {
  SCHEDULED: 'default',
  AWAITING_RESULT: 'info',
  REPORTED: 'success',
  POSTPONED: 'warning',
  OVERDUE: 'error',
}

interface MatchDayMatchesTableProps {
  readonly matches: readonly TrackedMatch[]
  readonly results: ResultsView
  readonly onIgnore: (match: TrackedMatch) => void
  readonly onUnignore: (match: TrackedMatch) => void
  readonly onNote: (match: TrackedMatch) => void
}

export function MatchDayMatchesTable({ matches, results, onIgnore, onUnignore, onNote }: MatchDayMatchesTableProps) {
  if (matches.length === 0) {
    return <Typography color="text.secondary">This match day has no tracked matches.</Typography>
  }
  return (
    <TableContainer>
      <Table size="small" aria-label="Matches">
        <TableHead>
          <TableRow>
            <TableCell>Date</TableCell>
            <TableCell>Home</TableCell>
            <TableCell>Away</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Result</TableCell>
            <TableCell>Reported at</TableCell>
            <TableCell>Ignored</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {matches.map((match) => (
            <TableRow key={match.matchId} hover>
              <TableCell>{formatInstant(match.matchDateTime)}</TableCell>
              <TableCell>{match.homeTeamName}</TableCell>
              <TableCell>{match.awayTeamName}</TableCell>
              <TableCell>
                <Chip size="small" color={STATUS_COLORS[match.status]} label={MATCH_STATUS_LABELS[match.status]} />
              </TableCell>
              <TableCell>{resultText(results, match.matchId)}</TableCell>
              <TableCell>
                {match.reportedAt === null ? (
                  '—'
                ) : (
                  <>
                    {formatInstant(match.reportedAt)}
                    {match.reportedRunId !== null && (
                      <>
                        {' '}
                        <Link component={RouterLink} to={`/runs/${match.reportedRunId}`}>
                          Run
                        </Link>
                      </>
                    )}
                  </>
                )}
              </TableCell>
              <TableCell>
                {match.ignoredAt === null ? '—' : `${match.ignoredBy ?? 'unknown'} · ${formatInstant(match.ignoredAt)}`}
              </TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>
                <Can capability="operate-match-days" mode="disable">
                  {match.ignoredAt === null ? (
                    <Button size="small" onClick={() => onIgnore(match)}>
                      Ignore
                    </Button>
                  ) : (
                    <Button size="small" onClick={() => onUnignore(match)}>
                      Stop ignoring
                    </Button>
                  )}
                </Can>
                <Can capability="operate-match-days" mode="disable">
                  <Button size="small" onClick={() => onNote(match)}>
                    Add note
                  </Button>
                </Can>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
