import StateIcon from './StateIcon.jsx'

/** An icon and a count per state. Decorative: the group's accessible name carries the same counts. */
function StateCounts({ states, className }) {
  return (
    <span className={className} aria-hidden="true">
      {states.map((entry) => (
        <span key={entry.state} className="state-count">
          <StateIcon state={entry.state} size={12} />
          {entry.count}
        </span>
      ))}
    </span>
  )
}

export default StateCounts
