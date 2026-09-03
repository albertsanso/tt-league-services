import { Star } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import Card from '../ui/Card.jsx'
import ErrorState from '../ui/ErrorState.jsx'

export default function ImportSourceSelector({ sources, selected, onSelect }) {
  const { t } = useTranslation()
  const statusAnnouncement = sources.error
    ? t('importPanel.statusError')
    : sources.loading
      ? t('importPanel.statusLoading')
      : t('importPanel.statusUpdated')

  return <div className="import-source-selector" aria-label={t('importPanel.sourcesTitle')}>
    <h2>{t('importPanel.sourcesTitle')}</h2>
    <p className="sr-only" aria-live="polite">{statusAnnouncement}</p>
    {sources.error ? <ErrorState action={sources.retry ? <button type="button" onClick={sources.retry}>{t('common.retry')}</button> : null}>
      {t(sources.error.status === 401
        ? 'importPanel.unauthorized'
        : sources.error.status === 403
          ? 'importPanel.forbidden'
          : 'importPanel.serverError')}
    </ErrorState> : null}
    {sources.data.map((source) => {
      const id = source.id ?? source.code
      const status = sources.loading ? 'loading' : (source.status ?? 'unavailable')
      return <Card as="article" className={selected === id ? 'import-source-card selected' : 'import-source-card'} key={id}>
        <button
          type="button"
          className="import-source-select"
          onClick={() => onSelect(id)}
          aria-pressed={selected === id}
          aria-label={t('importPanel.favourite', { source: source.label ?? id })}
          aria-describedby={`import-source-status-${id}`}
        >
          <strong>{source.label ?? id}</strong>
          <span>{t(`importPanel.sourceDescription.${id}`, { defaultValue: '' })}</span>
          <span
            id={`import-source-status-${id}`}
            className={`import-source-status import-source-status--${status}`}
            role="img"
            aria-label={t(`importPanel.sourceStatus.${status}`, { source: source.label ?? id })}
          >
            <Star size={18} fill={status === 'available' ? 'currentColor' : 'none'} aria-hidden="true" />
          </span>
        </button>
      </Card>
    })}
  </div>
}
