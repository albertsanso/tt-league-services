import {
  ArrowRight, CalendarOff, Check, Clock, Hourglass, TriangleAlert,
} from 'lucide-react'

const ICONS = {
  PLAYED: Check,
  UPCOMING: Clock,
  AWAITING_RESULT: Hourglass,
  OVERDUE: TriangleAlert,
  POSTPONED: ArrowRight,
  UNDATED: CalendarOff,
}

/** Decorative icon of a calendar state: the text next to it carries the meaning. */
function StateIcon({ state, size = 14 }) {
  const Icon = ICONS[state] ?? Clock
  return <Icon className={`state-icon state-icon-${String(state).toLowerCase()}`} aria-hidden="true" size={size} />
}

export default StateIcon
