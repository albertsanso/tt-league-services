import { useMemo, useState } from 'react'
import { addDays, parseISO } from 'date-fns'
import WeekDay from './WeekDay.jsx'
import { countStates, groupByDay } from './calendarEvents.js'
import { rangeFor, toIsoDate, todayIso } from './calendarRange.js'

const WEEK_DAYS = 7
const NO_MATCHES = []

function daysOf(date) {
  const start = parseISO(rangeFor('week', date).from)
  return Array.from({ length: WEEK_DAYS }, (_, offset) => toIsoDate(addDays(start, offset)))
}

/**
 * The week view: one column per day (stacked on small screens) whose matches are grouped by source and then
 * by competition. Every group starts collapsed; the open groups live in this component's state only, so the
 * parent resets them by giving the grid a new `key`. Closing a group closes the groups inside it.
 */
function WeekGrid({
  date, today = null, matches, fallbackSource = '', onDateSelect, onMatchSelect,
}) {
  const [open, setOpen] = useState(() => new Set())
  const days = useMemo(() => daysOf(date), [date])
  const byDay = useMemo(() => new Map(groupByDay(matches).map((group) => [group.day, group.matches])), [matches])
  const currentDay = today ?? todayIso()

  const toggle = (key) => setOpen((previous) => {
    const next = new Set(previous)
    if (next.has(key)) {
      next.delete(key)
      previous.forEach((other) => {
        if (other.startsWith(`${key}|`)) next.delete(other)
      })
    } else {
      next.add(key)
    }
    return next
  })

  return (
    <div className="week-grid">
      {days.map((day, index) => {
        const dayMatches = byDay.get(day) ?? NO_MATCHES
        return (
          <WeekDay
            key={day}
            day={day}
            index={index}
            summary={countStates(dayMatches)}
            matches={dayMatches}
            fallbackSource={fallbackSource}
            isToday={day === currentDay}
            open={open}
            onToggle={toggle}
            onDateSelect={onDateSelect}
            onMatchSelect={onMatchSelect}
          />
        )
      })}
    </div>
  )
}

export default WeekGrid
