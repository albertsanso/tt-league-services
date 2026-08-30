import { TrendingUp } from 'lucide-react'
import { useMemo } from 'react'
import { useCountUp } from '../../hooks/useCountUp.js'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n/index.js'

function StatCard({ label, value, delta, loading, status }) {
  const numericValue = useMemo(() => (typeof value === 'number' ? value : 0), [value])
  const { t } = useTranslation()
  const { ref, value: animatedValue } = useCountUp(numericValue)
  const displayValue =
    typeof value === 'number' ? new Intl.NumberFormat(i18n.language || 'ca-ES').format(animatedValue) : String(value || '-')

  return (
    <article className="stat-card card" ref={ref}>
      <p className="stat-label">{label}</p>
      <p className="stat-value">{loading ? <span className="skeleton" /> : displayValue}</p>

      {!loading && typeof delta === 'number' ? (
        <p className="stat-meta">
          <TrendingUp size={13} strokeWidth={1.5} aria-hidden="true" />
          +{new Intl.NumberFormat(i18n.language || 'ca-ES').format(delta)}
          <span className="stat-meta-text">{t('overview.currentSeason')}</span>
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
