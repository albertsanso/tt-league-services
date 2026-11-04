import Accordion from '@mui/material/Accordion'
import AccordionDetails from '@mui/material/AccordionDetails'
import AccordionSummary from '@mui/material/AccordionSummary'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { BarChart } from '@mui/x-charts/BarChart'
import type { TimeToReportResponse } from '../api/types'
import { formatHours, toHours } from './format'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/** Median and p90 time to report per source (the total rows) and per category (the expandable table). */
export function TimeToReportPanel({ data, season }: { readonly data: TimeToReportResponse | null; readonly season: string | null }) {
  const subtitle = season === null ? undefined : `Season ${season}. Only results that arrived while the match was tracked.`
  if (data === null) {
    return (
      <Panel title="Time to report" subtitle={subtitle}>
        <NoData>Select a season to see the time to report.</NoData>
      </Panel>
    )
  }
  const totals = data.rows.filter((row) => row.competition === null)
  const categories = data.rows.filter((row) => row.competition !== null)
  return (
    <Panel title="Time to report" subtitle={subtitle}>
      {data.rows.length === 0 ? (
        <NoData>No reported matches for this season.</NoData>
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: totals.map((row) => row.source) }]}
            series={[
              { data: totals.map((row) => toHours(row.medianSeconds)), label: 'Median (h)' },
              { data: totals.map((row) => toHours(row.p90Seconds)), label: 'p90 (h)' },
            ]}
          />
          <SummaryTable
            label="Time to report table"
            head={['Source', 'Matches', 'Median', 'p90']}
            rows={totals.map((row) => [row.source, row.count, formatHours(row.medianSeconds), formatHours(row.p90Seconds)])}
          />
          <Accordion disableGutters variant="outlined" sx={{ mt: 1 }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>By category</AccordionSummary>
            <AccordionDetails>
              <SummaryTable
                label="Time to report by category"
                head={['Source', 'Category', 'Matches', 'Median', 'p90']}
                rows={categories.map((row) => [
                  row.source,
                  row.competition ?? '',
                  row.count,
                  formatHours(row.medianSeconds),
                  formatHours(row.p90Seconds),
                ])}
              />
            </AccordionDetails>
          </Accordion>
        </>
      )}
    </Panel>
  )
}
