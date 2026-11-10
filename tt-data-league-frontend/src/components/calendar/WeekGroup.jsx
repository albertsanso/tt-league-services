import { ChevronDown, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import StateCounts from './StateCounts.jsx'
import { stateSummary } from './stateSummary.js'

/**
 * A collapsible group of the week view (a source, or a competition inside it): a native button that names
 * the group and its counts, and the content it controls, which is only rendered while the group is open.
 */
function WeekGroup({
  level, id, name, tag = false, total, states, open, onToggle, children,
}) {
  const { t } = useTranslation()
  const Chevron = open ? ChevronDown : ChevronRight

  return (
    <div className={`week-group week-group-${level}`}>
      <button
        type="button"
        className="week-group-toggle"
        aria-expanded={open}
        aria-controls={id}
        aria-label={`${name}, ${stateSummary(t, total, states)}`}
        onClick={onToggle}
      >
        <span className="week-group-head">
          <Chevron className="week-group-chevron" aria-hidden="true" size={14} />
          <span className={tag ? 'cal-source-tag' : 'week-group-name'}>{name}</span>
          <span className="week-group-total" aria-hidden="true">{total}</span>
        </span>
        <StateCounts states={states} className="week-group-states" />
      </button>
      <div id={id} className="week-group-body" hidden={!open}>
        {open ? children : null}
      </div>
    </div>
  )
}

export default WeekGroup
