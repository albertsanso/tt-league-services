import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { useMemo, useState } from 'react'
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom'
import type { TriggerRunOutcome } from '../api/runs'
import type { MatchDayDetail, MatchResult } from '../api/types'
import { Can } from '../auth/Can'
import { MatchDayActionDialog } from '../calendar/MatchDayActionDialog'
import type { MatchDayAction } from '../calendar/MatchDayActionDialog'
import { MatchDayHeader } from '../calendar/MatchDayHeader'
import { MatchDayMatchesTable } from '../calendar/MatchDayMatchesTable'
import { MatchDayTimeline } from '../calendar/MatchDayTimeline'
import { RefreshGroupDialog } from '../calendar/RefreshGroupDialog'
import type { ResultsView } from '../calendar/results'
import { buildTimeline } from '../calendar/timeline'
import { useMatchDayDetail } from '../calendar/useMatchDayDetail'
import { useMatchDayResults } from '../calendar/useMatchDayResults'

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

function RefreshSummary({ outcome, onDismiss }: { outcome: TriggerRunOutcome; onDismiss: () => void }) {
  return (
    <Alert severity="success" onClose={onDismiss} sx={{ mt: 2 }}>
      <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
        {outcome.response.results.map((result) => (
          <li key={result.source}>
            {result.source} —{' '}
            {result.outcome === 'CREATED' && result.run !== undefined ? (
              <>
                refresh run created.{' '}
                <Link component={RouterLink} to={`/runs/${result.run.id}`}>
                  Open run
                </Link>
              </>
            ) : result.outcome === 'QUEUED' ? (
              'refresh queued; it starts when the active run finishes.'
            ) : (
              (result.message ?? result.outcome.toLowerCase())
            )}
          </li>
        ))}
      </ul>
    </Alert>
  )
}

export default function MatchDayDetailPage() {
  const { matchDayId = '' } = useParams()
  const location = useLocation()
  const backTo = (location.state as { backTo?: string } | null)?.backTo ?? ''
  const { detail, loading, error, notFound, refetch, replace } = useMatchDayDetail(matchDayId)
  const results = useMatchDayResults(matchDayId, detail === null ? null : detail.matchDay.reportedMatches)

  const [action, setAction] = useState<MatchDayAction | null>(null)
  const [refreshOpen, setRefreshOpen] = useState(false)
  const [refreshed, setRefreshed] = useState<TriggerRunOutcome | null>(null)

  const resultsView = useMemo<ResultsView>(() => {
    if (results.error !== null) {
      return { state: 'unavailable' }
    }
    if (results.results === null) {
      return { state: 'loading' }
    }
    const byMatch = new Map<string, MatchResult>(results.results.results.map((result) => [result.matchId, result]))
    return { state: 'loaded', byMatch }
  }, [results.error, results.results])

  const timeline = useMemo(() => {
    if (detail === null) {
      return []
    }
    const labels = new Map(detail.matches.map((match) => [match.matchId, `${match.homeTeamName} – ${match.awayTeamName}`]))
    return buildTimeline(detail.events, detail.runs, labels)
  }, [detail])

  const back = (
    <Link component={RouterLink} to={`/calendar${backTo}`}>
      Calendar
    </Link>
  )

  if (notFound) {
    return (
      <>
        <Box sx={{ mb: 2 }}>{back}</Box>
        <Typography variant="h5" component="h2">
          Match day not found
        </Typography>
      </>
    )
  }

  const apply = (next: MatchDayDetail) => replace(next)

  return (
    <>
      <Box sx={{ mb: 2 }}>{back}</Box>
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
      {loading && detail === null && <LinearProgress aria-label="Loading match day" />}
      {detail !== null && (
        <>
          <MatchDayHeader summary={detail.matchDay} />

          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 2 }}>
            <Can capability="trigger-runs" mode="disable">
              <Button variant="contained" onClick={() => setRefreshOpen(true)}>
                Refresh group
              </Button>
            </Can>
            <Can capability="operate-match-days" mode="disable">
              {detail.matchDay.state === 'CLOSED' ? (
                <Button variant="outlined" onClick={() => setAction({ kind: 'reopen' })}>
                  Reopen match day
                </Button>
              ) : (
                <Button variant="outlined" onClick={() => setAction({ kind: 'close' })}>
                  Close match day
                </Button>
              )}
            </Can>
            <Can capability="operate-match-days" mode="disable">
              <Button variant="outlined" onClick={() => setAction({ kind: 'note', matchId: null })}>
                Add note
              </Button>
            </Can>
          </Box>
          {refreshed !== null && <RefreshSummary outcome={refreshed} onDismiss={() => setRefreshed(null)} />}

          <Section title="Matches">
            {results.error !== null && (
              <Alert
                severity="warning"
                sx={{ mb: 1 }}
                action={
                  <Button color="inherit" size="small" onClick={results.retry}>
                    Retry
                  </Button>
                }
              >
                {`Results could not be read from the platform: ${results.error}`}
              </Alert>
            )}
            <MatchDayMatchesTable
              matches={detail.matches}
              results={resultsView}
              onIgnore={(match) => setAction({ kind: 'ignore', match })}
              onUnignore={(match) => setAction({ kind: 'unignore', match })}
              onNote={(match) => setAction({ kind: 'note', matchId: match.matchId })}
            />
          </Section>

          <Section title="Timeline">
            <MatchDayTimeline items={timeline} />
          </Section>

          {action !== null && (
            <MatchDayActionDialog
              matchDayId={matchDayId}
              action={action}
              matches={detail.matches}
              onDone={apply}
              onConflict={refetch}
              onClose={() => setAction(null)}
            />
          )}
          {refreshOpen && (
            <RefreshGroupDialog
              matchDayId={matchDayId}
              source={detail.matchDay.source}
              onRefreshed={setRefreshed}
              onClose={() => setRefreshOpen(false)}
            />
          )}
        </>
      )}
    </>
  )
}
