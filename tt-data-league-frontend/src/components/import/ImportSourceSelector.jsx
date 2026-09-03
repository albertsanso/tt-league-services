import { Star } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import Card from '../ui/Card.jsx'
import LoadingState from '../ui/LoadingState.jsx'
import EmptyState from '../ui/EmptyState.jsx'
import ErrorState from '../ui/ErrorState.jsx'

export default function ImportSourceSelector({ sources, selected, onSelect }) {
  const { t } = useTranslation()
  const [favourites, setFavourites] = useState([])
  if (sources.loading) return <LoadingState>{t('importPanel.loading')}</LoadingState>
  if (sources.error) return <ErrorState>{t(sources.error.status === 403 ? 'importPanel.forbidden' : 'importPanel.serverError')}</ErrorState>
  if (!sources.data.length) return <EmptyState>{t('importPanel.sourcesEmpty')}</EmptyState>
  return <div className="import-source-selector" aria-label={t('importPanel.sourcesTitle')}>
    <h2>{t('importPanel.sourcesTitle')}</h2>
    {sources.data.map((source) => {
      const id = source.id ?? source.code
      const favourite = favourites.includes(id)
      return <Card as="article" className={selected === id ? 'import-source-card selected' : 'import-source-card'} key={id}>
        <button type="button" className="import-source-select" onClick={() => onSelect(id)} aria-pressed={selected === id}>
          <strong>{source.label ?? id}</strong>
          <span>{t(`importPanel.sourceDescription.${id}`, { defaultValue: '' })}</span>
        </button>
        <button type="button" className="import-favourite" aria-label={t('importPanel.favourite', { source: source.label ?? id })} aria-pressed={favourite} onClick={() => setFavourites((current) => favourite ? current.filter((value) => value !== id) : [...current, id])}><Star size={16} aria-hidden="true" /></button>
      </Card>
    })}
  </div>
}
