import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Skeleton from '@mui/material/Skeleton'
import Typography from '@mui/material/Typography'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMatchDayFacets } from '../calendar/useMatchDayFacets'
import { CorrectionsPanel } from '../statistics/CorrectionsPanel'
import { DailyOverviewPanel } from '../statistics/DailyOverviewPanel'
import { PendingByAgePanel } from '../statistics/PendingByAgePanel'
import { ReportingProgressPanel } from '../statistics/ReportingProgressPanel'
import { RunOutcomesPanel } from '../statistics/RunOutcomesPanel'
import { SourceHealthPanel } from '../statistics/SourceHealthPanel'
import {
  defaultRange,
  parseStatisticsFilters,
  rangeError,
  serializeStatisticsFilters,
} from '../statistics/statisticsFilters'
import type { StatisticsFilters } from '../statistics/statisticsFilters'
import { StatisticsFilterBar } from '../statistics/StatisticsFilterBar'
import { TimeToReportPanel } from '../statistics/TimeToReportPanel'
import { useStatistics } from '../statistics/useStatistics'
import type { StatisticsData } from '../statistics/useStatistics'

function hasAnyData(data: StatisticsData): boolean {
  return (
    data.daily.rows.length > 0 ||
    data.runs.days.length > 0 ||
    data.corrections.days.length > 0 ||
    data.sourceHealth.days.length > 0 ||
    (data.timeToReport?.rows.length ?? 0) > 0 ||
    data.pending.sources.some(
      (row) => row.under1Day + row.days1To2 + row.days2To7 + row.over7Days > 0,
    )
  )
}

/** The newest season the facets offer (`YYYY-YYYY` sorts as text), whatever order they come in. */
function latestSeason(seasons: readonly string[]): string | null {
  return seasons.reduce<string | null>((latest, season) => (latest === null || season > latest ? season : latest), null)
}

function LoadingSkeletons() {
  return (
    <Box aria-label="Loading statistics" sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' } }}>
      {[0, 1, 2, 3].map((index) => (
        <Skeleton key={index} variant="rounded" height={260} />
      ))}
    </Box>
  )
}

export default function StatisticsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => parseStatisticsFilters(searchParams), [searchParams])
  const singleSource = filters.sources.length === 1 ? filters.sources[0] : null
  const { facets, error: facetsError } = useMatchDayFacets(singleSource, null)

  // Until the facets answer, the season is unknown; waiting avoids a second load when it resolves.
  const season = filters.season ?? latestSeason(facets?.seasons ?? [])
  const seasonPending = filters.season === null && facets === null && facetsError === null
  const invalidRange = rangeError(filters) !== null
  const query = useMemo(
    () =>
      invalidRange || seasonPending
        ? null
        : { sources: filters.sources, season, from: filters.fromDate, to: filters.toDate },
    [invalidRange, seasonPending, filters.sources, season, filters.fromDate, filters.toDate],
  )
  const { data, loading, error, reload } = useStatistics(query)

  const write = (next: Omit<StatisticsFilters, 'errors'>, replace: boolean) =>
    setSearchParams(serializeStatisticsFilters(next), { replace })
  const change = (
    patch: Partial<Pick<StatisticsFilters, 'sources' | 'season' | 'fromDate' | 'toDate'>>,
    replace: boolean,
  ) => write({ ...filters, ...patch }, replace)
  const reset = () => write({ sources: [], season: null, ...defaultRange(new Date()) }, false)

  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        Statistics
      </Typography>
      {filters.errors.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {filters.errors.map((message) => (
            <div key={message}>{message}</div>
          ))}
        </Alert>
      )}
      <StatisticsFilterBar
        filters={filters}
        season={season}
        seasons={facets?.seasons ?? []}
        onChange={change}
        onReset={reset}
        onRefresh={reload}
        refreshing={loading}
      />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {data === null
          ? 'Days are grouped in the server time zone.'
          : `Days are grouped in the server time zone (${data.daily.zone}).`}
      </Typography>

      {error !== null && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={reload}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {data === null ? (
        error === null && !invalidRange && <LoadingSkeletons />
      ) : !hasAnyData(data) ? (
        <Typography color="text.secondary">No statistics for this range</Typography>
      ) : (
        <Box
          sx={{
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' },
            opacity: loading ? 0.6 : 1,
          }}
        >
          <DailyOverviewPanel data={data.daily} />
          <RunOutcomesPanel data={data.runs} />
          <TimeToReportPanel data={data.timeToReport} season={season} />
          <PendingByAgePanel data={data.pending} />
          <CorrectionsPanel data={data.corrections} />
          <SourceHealthPanel data={data.sourceHealth} />
        </Box>
      )}

      {data !== null && hasAnyData(data) && (
        <Box sx={{ mt: 2 }}>
          <ReportingProgressPanel season={season} defaultSource={singleSource ?? filters.sources[0] ?? 'RFETM'} />
        </Box>
      )}
    </>
  )
}
