import { BarChart } from '@mui/x-charts/BarChart'
import type { CorrectionsResponse } from '../api/types'
import { hasSeveral, rowLabel } from './chartLabels'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/** Amended actas re-applied after the first report. Zero unless the platform runs with amended-acta detection. */
export function CorrectionsPanel({ data }: { readonly data: CorrectionsResponse }) {
  const { days, totals } = data
  const several = hasSeveral(days.map((day) => day.source))
  return (
    <Panel
      title="Corrections after first report"
      subtitle="Stays at zero unless the platform runs with amended-acta detection enabled."
    >
      {days.length === 0 ? (
        <NoData />
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: days.map((day) => rowLabel(day.date, day.source, several)) }]}
            series={[{ data: days.map((day) => day.amendedPlayed), label: 'Amended actas re-applied' }]}
          />
          <SummaryTable
            label="Corrections table"
            head={['Day', 'Source', 'Amended']}
            rows={days.map((day) => [day.date, day.source, day.amendedPlayed])}
          />
          <SummaryTable
            label="Corrections totals"
            head={['Source', 'Total amended']}
            rows={totals.map((total) => [total.source, total.amendedPlayed])}
          />
        </>
      )}
    </Panel>
  )
}
