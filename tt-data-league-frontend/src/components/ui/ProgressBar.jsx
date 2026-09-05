function clampPercentage(value) {
  if (value === null || value === undefined || Number.isNaN(value)) return null
  return Math.max(0, Math.min(100, value))
}

/**
 * Accessible progress bar. When `value` is null the total cannot be determined yet, so the track
 * renders an indeterminate animation instead of a specific fill percentage.
 */
function ProgressBar({ value, label, valueText }) {
  const percentage = clampPercentage(value)
  const indeterminate = percentage === null

  return (
    <div className="progress-bar">
      <div
        className={`progress-bar-track${indeterminate ? ' progress-bar-track--indeterminate' : ''}`}
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={indeterminate ? undefined : Math.round(percentage)}
        aria-valuetext={valueText}
      >
        {!indeterminate && (
          <div className="progress-bar-fill" style={{ width: `${percentage}%` }} />
        )}
      </div>
      {valueText && <span className="progress-bar-value-text">{valueText}</span>}
    </div>
  )
}

export default ProgressBar
