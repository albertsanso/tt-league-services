import { BarChart } from '@mui/x-charts/BarChart'
import type { UnitOutcomesResponse } from '../api/types'
import { formatDuration } from '../runs/format'
import { hasSeveral } from './chartLabels'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

/**
 * Terminal units by outcome per source and unit key, so the groups that fail often stand out. The figures are the
 * server ones; the panel only lays them out.
 */
export function UnitOutcomesPanel({ data }: { readonly data: UnitOutcomesResponse }) {
  const { units } = data
  const several = hasSeveral(units.map((unit) => unit.source))
  return (
    <Panel title="Units by outcome" subtitle="Every unit of a run counts once, whatever the outcome of its run.">
      {units.length === 0 ? (
        <NoData />
      ) : (
        <>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: units.map((unit) => (several ? `${unit.label} ${unit.source}` : unit.label)) }]}
            series={[
              { data: units.map((unit) => unit.succeeded), label: 'Succeeded', stack: 'outcome' },
              { data: units.map((unit) => unit.noChanges), label: 'No changes', stack: 'outcome' },
              { data: units.map((unit) => unit.partial), label: 'Partial', stack: 'outcome' },
              { data: units.map((unit) => unit.failed), label: 'Failed', stack: 'outcome' },
              { data: units.map((unit) => unit.skipped), label: 'Skipped', stack: 'outcome' },
            ]}
          />
          <SummaryTable
            label="Units by outcome table"
            head={['Unit', 'Source', 'Succeeded', 'No changes', 'Partial', 'Failed', 'Skipped', 'Average duration']}
            rows={units.map((unit) => [
              unit.label,
              unit.source,
              unit.succeeded,
              unit.noChanges,
              unit.partial,
              unit.failed,
              unit.skipped,
              formatDuration(unit.avgSeconds === null ? null : unit.avgSeconds * 1000),
            ])}
          />
        </>
      )}
    </Panel>
  )
}
