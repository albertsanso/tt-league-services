import { shortDate } from './format'

/** Chart label of one server row: the day, plus the source when the rows cover more than one. */
export function rowLabel(date: string, source: string, multipleSources: boolean): string {
  return multipleSources ? `${shortDate(date)} ${source}` : shortDate(date)
}

export function hasSeveral(values: readonly string[]): boolean {
  return new Set(values).size > 1
}
