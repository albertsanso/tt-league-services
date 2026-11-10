import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { addDays, format, parseISO } from 'date-fns'
import StateIcon from './StateIcon.jsx'
import { summarizeDays } from './calendarEvents.js'
import { stateSummary } from './stateSummary.js'
import {
  dateFnsLocale, rangeFor, toIsoDate, todayIso,
} from './calendarRange.js'

const WEEK_DAYS = 7
/** The states a compact cell flags with a single icon, most urgent first. */
const COMPACT_PRIORITY = ['OVERDUE', 'AWAITING_RESULT', 'POSTPONED']

function daysOf(date) {
  const { from, to } = rangeFor('month', date)
  const days = []
  for (let day = parseISO(from); toIsoDate(day) < to; day = addDays(day, 1)) days.push(toIsoDate(day))
  return days
}

function DayCell({
  day, summary, locale, inMonth, isToday, compact, onSelect,
}) {
  const { t } = useTranslation()
  const total = summary?.total ?? 0
  const states = summary?.states ?? []
  const flagged = compact ? COMPACT_PRIORITY.find((state) => states.some((entry) => entry.state === state)) : null
  const shown = compact ? states.filter((entry) => entry.state === flagged) : states
  const label = `${t('calendarPage.dayCellLabel', {
    date: format(parseISO(day), 'PPPP', { locale }),
    summary: stateSummary(t, total, states),
  })}. ${t('calendarPage.openDay')}`
  const classes = ['month-cell', inMonth ? '' : 'month-cell-outside', isToday ? 'month-cell-today' : '']
    .filter(Boolean).join(' ')

  return (
    <button
      type="button"
      className={classes}
      aria-label={label}
      aria-current={isToday ? 'date' : undefined}
      onClick={() => onSelect?.(day)}
    >
      <span className="month-cell-head">
        <span className="month-cell-day">{Number(day.slice(8))}</span>
        {total > 0 ? <span className="month-cell-total">{total}</span> : null}
      </span>
      <span className="month-cell-states" aria-hidden="true">
        {shown.map((entry) => (
          <span key={entry.state} className="month-cell-state">
            <StateIcon state={entry.state} size={12} />
            {compact ? null : entry.count}
          </span>
        ))}
      </span>
    </button>
  )
}

function MonthGrid({
  date, today = null, matches, compact = false, onDateSelect,
}) {
  const { i18n } = useTranslation()
  const locale = dateFnsLocale(i18n.language)
  const days = useMemo(() => daysOf(date), [date])
  const summary = useMemo(() => summarizeDays(matches), [matches])
  const currentDay = today ?? todayIso()
  const month = date.slice(0, 7)

  return (
    <div className="month-grid">
      <div className="month-grid-weekdays" aria-hidden="true">
        {days.slice(0, WEEK_DAYS).map((day) => (
          <span key={day}>{format(parseISO(day), 'EEE', { locale })}</span>
        ))}
      </div>
      <div className="month-grid-cells">
        {days.map((day) => (
          <DayCell
            key={day}
            day={day}
            summary={summary.get(day)}
            locale={locale}
            inMonth={day.startsWith(month)}
            isToday={day === currentDay}
            compact={compact}
            onSelect={onDateSelect}
          />
        ))}
      </div>
    </div>
  )
}

export default MonthGrid
