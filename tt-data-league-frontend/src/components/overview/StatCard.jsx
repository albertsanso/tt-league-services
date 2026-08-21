import { TrendingUp } from 'lucide-react'
import { useMemo } from 'react'
import { useCountUp } from '../../hooks/useCountUp.js'

const formatter = new Intl.NumberFormat('ca-ES')

function StatCard({ label, value, delta, loading, status }) {
  const numericValue = useMemo(() => (typeof value === 'number' ? value : 0), [value])
  const { ref, value: animatedValue } = useCountUp(numericValue)
  const displayValue =
    typeof value === 'number' ? formatter.format(animatedValue) : String(value || '-')

  return (
    <article className="stat-card card" ref={ref}>
      <p className="stat-label">{label}</p>
      <p className="stat-value">{loading ? <span className="skeleton" /> : displayValue}</p>

      {!loading && typeof delta === 'number' ? (
        <p className="stat-meta">
          <TrendingUp size={13} strokeWidth={1.5} aria-hidden="true" />
          +{formatter.format(delta)}
          <span className="stat-meta-text">aquesta temp.</span>
        </p>
      ) : null}

      {!loading && status ? (
        <p className="season-status">
          <span className="season-status-dot" aria-hidden="true" />
          {status}
        </p>
      ) : null}
    </article>
  )
}

export default StatCard
