import RefreshIcon from '@mui/icons-material/Refresh'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import MenuItem from '@mui/material/MenuItem'
import Skeleton from '@mui/material/Skeleton'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { PollingPolicy, PollSchedule } from '../api/types'
import { useApi } from '../api/useApi'
import { ConfirmActionDialog } from '../polling/ConfirmActionDialog'
import { formatIsoDuration } from '../polling/format'
import { PolicyEditDialog } from '../polling/PolicyEditDialog'
import { PolicyTable } from '../polling/PolicyTable'
import { parsePollingFilters, resolvePollingFilters, serializePollingFilters } from '../polling/pollingFilters'
import { PollingModeCard } from '../polling/PollingModeCard'
import { ScheduleTable } from '../polling/ScheduleTable'
import { usePollingOverview } from '../polling/usePollingOverview'
import { usePollSchedules } from '../polling/usePollSchedules'
import { SOURCES } from '../runs/runFilters'
import { isValidSeason } from '../statistics/statisticsFilters'

type OpenDialog =
  | { readonly kind: 'edit'; readonly policy: PollingPolicy }
  | { readonly kind: 'reset'; readonly policy: PollingPolicy }
  | { readonly kind: 'resume'; readonly schedule: PollSchedule }

export default function PollingPage() {
  const api = useApi()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => parsePollingFilters(searchParams), [searchParams])
  const overview = usePollingOverview()
  const { source, season } = resolvePollingFilters(filters, overview.data?.status ?? null)
  const schedules = usePollSchedules(source, overview.data === null ? null : season)
  const [dialog, setDialog] = useState<OpenDialog | null>(null)
  const [seasonDraft, setSeasonDraft] = useState<string | null>(null)

  const selectedPolicy = overview.data?.policies.find((policy) => policy.source === source)
  const selectedMode = overview.data?.status.sources.find((entry) => entry.source === source)?.mode
  const write = (next: { source: typeof source | null; season: string | null }) =>
    setSearchParams(serializePollingFilters(next), { replace: false })
  const seasonText = seasonDraft ?? season ?? ''
  const seasonInvalid = seasonDraft !== null && seasonDraft !== '' && !isValidSeason(seasonDraft)

  const refresh = () => {
    overview.reload()
    schedules.reload()
  }

  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        Polling
      </Typography>
      {filters.errors.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {filters.errors.map((message) => (
            <div key={message}>{message}</div>
          ))}
        </Alert>
      )}
      {overview.error !== null && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={overview.reload}>
              Retry
            </Button>
          }
        >
          {overview.error}
        </Alert>
      )}

      {overview.data === null ? (
        overview.error === null && (
          <Box aria-label="Loading polling" sx={{ display: 'grid', gap: 2 }}>
            <Skeleton variant="rounded" height={160} />
            <Skeleton variant="rounded" height={260} />
          </Box>
        )
      ) : (
        <Box sx={{ display: 'grid', gap: 2, opacity: overview.loading ? 0.6 : 1 }}>
          <PollingModeCard status={overview.data.status} />
          <PolicyTable
            status={overview.data.status}
            policies={overview.data.policies}
            onEdit={(policy) => setDialog({ kind: 'edit', policy })}
            onReset={(policy) => setDialog({ kind: 'reset', policy })}
          />
          <Card variant="outlined" component="section" aria-label="Poll schedules">
            <CardContent>
              <Typography variant="h6" component="h3">
                Schedules
              </Typography>
              <Box
                role="group"
                aria-label="Schedule filters"
                sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start', my: 1 }}
              >
                <TextField
                  select
                  size="small"
                  label="Source"
                  sx={{ minWidth: 160 }}
                  value={source}
                  onChange={(event) => write({ source: event.target.value as typeof source, season: filters.season })}
                >
                  {SOURCES.map((value) => (
                    <MenuItem key={value} value={value}>
                      {value}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  size="small"
                  label="Season"
                  placeholder="2026-2027"
                  value={seasonText}
                  error={seasonInvalid}
                  helperText={seasonInvalid ? 'Use the form 2026-2027' : undefined}
                  onChange={(event) => {
                    const text = event.target.value.trim()
                    setSeasonDraft(text)
                    if (text === '') {
                      write({ source: filters.source, season: null })
                      setSeasonDraft(null)
                    } else if (isValidSeason(text)) {
                      write({ source: filters.source, season: text })
                      setSeasonDraft(null)
                    }
                  }}
                />
                <Button startIcon={<RefreshIcon />} onClick={refresh} disabled={overview.loading || schedules.loading}>
                  Refresh
                </Button>
              </Box>
              {selectedPolicy !== undefined && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {`Only the last ${selectedPolicy.recentMatchDays} match ${
                    selectedPolicy.recentMatchDays === 1 ? 'day' : 'days'
                  } of each group are polled; older ones are covered by the full refresh (every ${formatIsoDuration(
                    selectedPolicy.fullRefresh,
                  )}).`}
                </Typography>
              )}
              {selectedMode !== undefined && selectedMode !== 'ADAPTIVE' && (
                <Alert severity="info" sx={{ mb: 1 }}>
                  {`${source} is not polled adaptively, so it gets no new schedules.`}
                </Alert>
              )}
              {schedules.error !== null && (
                <Alert
                  severity="error"
                  sx={{ mb: 1 }}
                  action={
                    <Button color="inherit" size="small" onClick={schedules.reload}>
                      Retry
                    </Button>
                  }
                >
                  {schedules.error}
                </Alert>
              )}
              {season === null ? (
                <Typography color="text.secondary">Choose a season to see its schedules.</Typography>
              ) : schedules.schedules.length === 0 ? (
                schedules.loading ? (
                  <Skeleton variant="rounded" height={120} aria-label="Loading schedules" />
                ) : (
                  schedules.error === null && (
                    <Typography color="text.secondary">
                      {`No schedules for ${source} in ${season}. They appear after the first adaptive tick.`}
                    </Typography>
                  )
                )
              ) : (
                <Box sx={{ opacity: schedules.loading ? 0.6 : 1 }}>
                  <ScheduleTable
                    schedules={schedules.schedules}
                    onResume={(schedule) => setDialog({ kind: 'resume', schedule })}
                  />
                </Box>
              )}
            </CardContent>
          </Card>
        </Box>
      )}

      {dialog?.kind === 'edit' && (
        <PolicyEditDialog policy={dialog.policy} onChanged={overview.reload} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'reset' && (
        <ConfirmActionDialog
          title={`Reset polling policy of ${dialog.policy.source}`}
          confirmLabel="Reset to defaults"
          action={() => api.polling.deletePollingPolicy(dialog.policy.source)}
          onDone={overview.reload}
          onConflict={overview.reload}
          onClose={() => setDialog(null)}
        >
          <Typography>
            Remove the stored override of {dialog.policy.source}. The configured defaults apply again from the next tick.
          </Typography>
        </ConfirmActionDialog>
      )}
      {dialog?.kind === 'resume' && (
        <ConfirmActionDialog
          title="Resume stopped unit"
          confirmLabel="Resume"
          action={() => api.polling.resumePollSchedule(dialog.schedule.id)}
          onDone={schedules.reload}
          onConflict={schedules.reload}
          onClose={() => setDialog(null)}
        >
          <Typography>
            The unit is polled at the next tick and can stop again after one more poll if it is still overdue.
          </Typography>
        </ConfirmActionDialog>
      )}
    </>
  )
}
