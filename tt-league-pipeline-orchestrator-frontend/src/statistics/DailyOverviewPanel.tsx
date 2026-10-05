import { BarChart } from '@mui/x-charts/BarChart'
import type { DailyStatsResponse } from '../api/types'
import { formatHours, toHours } from './format'
import { hasSeveral, rowLabel } from './chartLabels'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/** Runs, failures, matches reported and the average time to report per day, from the stored daily snapshots. */
export function DailyOverviewPanel({ data }: { readonly data: DailyStatsResponse }) {
  const { rows } = data
  const several = hasSeveral(rows.map((row) => row.source))
  const labels = rows.map((row) => rowLabel(row.date, row.source, several))
  return (
    <Panel title="Daily overview" subtitle="One row per day and source, written once the day is complete.">
      {rows.length === 0 ? (
        <NoData />
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: labels }]}
            series={[
              { data: rows.map((row) => row.runs), label: 'Runs' },
              { data: rows.map((row) => row.failures), label: 'Failures' },
              { data: rows.map((row) => row.matchesReported), label: 'Matches reported' },
            ]}
          />
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: labels }]}
            series={[{ data: rows.map((row) => toHours(row.avgTimeToReportSeconds)), label: 'Average time to report (h)' }]}
          />
          <SummaryTable
            label="Daily overview table"
            head={['Day', 'Source', 'Runs', 'Failures', 'Reported', 'Avg time to report', 'Pending at end of day']}
            rows={rows.map((row) => [
              row.date,
              row.source,
              row.runs,
              row.failures,
              row.matchesReported,
              formatHours(row.avgTimeToReportSeconds),
              row.pendingEndOfDay,
            ])}
          />
        </>
      )}
    </Panel>
  )
}
