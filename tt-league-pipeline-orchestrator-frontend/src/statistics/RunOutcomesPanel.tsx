import { BarChart } from '@mui/x-charts/BarChart'
import type { RunOutcomesResponse } from '../api/types'
import { formatDuration } from '../runs/format'
import { STEP_LABELS } from '../runs/runStatus'
import { hasSeveral, rowLabel } from './chartLabels'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/** Terminal runs by outcome per day and source, and the average duration of finished step attempts. */
export function RunOutcomesPanel({ data }: { readonly data: RunOutcomesResponse }) {
  const { days, stepAverages } = data
  const several = hasSeveral(days.map((day) => day.source))
  return (
    <Panel title="Runs by outcome">
      {days.length === 0 ? (
        <NoData />
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: days.map((day) => rowLabel(day.date, day.source, several)) }]}
            series={[
              { data: days.map((day) => day.succeeded), label: 'Succeeded', stack: 'outcome' },
              { data: days.map((day) => day.noChanges), label: 'No changes', stack: 'outcome' },
              { data: days.map((day) => day.partial), label: 'Partial', stack: 'outcome' },
              { data: days.map((day) => day.failed), label: 'Failed', stack: 'outcome' },
            ]}
          />
          <SummaryTable
            label="Runs by outcome table"
            head={['Day', 'Source', 'Succeeded', 'No changes', 'Partial', 'Failed']}
            rows={days.map((day) => [day.date, day.source, day.succeeded, day.noChanges, day.partial, day.failed])}
          />
        </>
      )}
      {stepAverages.length > 0 && (
        <SummaryTable
          label="Average step duration"
          head={['Source', 'Step', 'Attempts', 'Average duration']}
          rows={stepAverages.map((average) => [
            average.source,
            STEP_LABELS[average.kind],
            average.attempts,
            formatDuration(average.avgStepSeconds === null ? null : average.avgStepSeconds * 1000),
          ])}
        />
      )}
    </Panel>
  )
}
