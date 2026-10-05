import { BarChart } from '@mui/x-charts/BarChart'
import type { PendingResponse } from '../api/types'
import { formatInstant } from '../runs/format'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/** Matches still waiting for a result, by how long they have been waiting. */
export function PendingByAgePanel({ data }: { readonly data: PendingResponse }) {
  const { sources } = data
  return (
    <Panel title="Pending by age" subtitle={`As of ${formatInstant(data.asOf)}. Age counts from the match date.`}>
      {sources.length === 0 ? (
        <NoData />
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: sources.map((row) => row.source) }]}
            series={[
              { data: sources.map((row) => row.under1Day), label: 'Under 1 day', stack: 'age' },
              { data: sources.map((row) => row.days1To2), label: '1 to 2 days', stack: 'age' },
              { data: sources.map((row) => row.days2To7), label: '2 to 7 days', stack: 'age' },
              { data: sources.map((row) => row.over7Days), label: 'Over 7 days', stack: 'age' },
            ]}
          />
          <SummaryTable
            label="Pending by age table"
            head={['Source', 'Under 1 day', '1 to 2 days', '2 to 7 days', 'Over 7 days', 'Overdue']}
            rows={sources.map((row) => [row.source, row.under1Day, row.days1To2, row.days2To7, row.over7Days, row.overdue])}
          />
        </>
      )}
    </Panel>
  )
}
