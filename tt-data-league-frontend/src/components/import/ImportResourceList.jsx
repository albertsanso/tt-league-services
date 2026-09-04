import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'

const tone = (status) => {
  const normalized = status?.toUpperCase()
  return ['COMPLETED', 'PROCESSED', 'SUCCESS', 'DONE'].includes(normalized)
    ? 'success'
    : ['FAILED', 'ERROR'].includes(normalized)
      ? 'error'
      : status
        ? 'warning'
        : 'subtle'
}

function displayValue(value, fallback) {
  return value || fallback
}

function formatUploadDate(value, fallback) {
  if (!value) return fallback
  const match = String(value).match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2})/)
  if (match) return `${match[1]} ${match[2]}:${match[3]}`
  return fallback
}

export default function ImportResourceList({ resources, onSimulate, onImport }) {
  const { t } = useTranslation()

  return <section className="import-resource-list" aria-labelledby="import-resources-title">
    <h2 id="import-resources-title">{t('importPanel.resourcesTitle')}</h2>
    <div className="import-resource-items" role="list">
      {resources.map((resource) => <Card as="article" className="import-resource-item" key={resource.id} role="listitem">
        <div className="import-resource-actions">
          <Button variant="secondary" onClick={() => onSimulate(resource)}>{t('importPanel.simulate')}</Button>
          <Button variant="primary" onClick={() => onImport(resource)}>{t('importPanel.import')}</Button>
        </div>
        <div className="import-resource-content">
          <div className="import-resource-heading">
            <Badge tone={tone(resource.status)}>{displayValue(resource.status, t('importPanel.resourceReady'))}</Badge>
          </div>
          <dl className="import-resource-details">
            <div className="import-resource-emphasis"><dt>{t('importPanel.resourceType')}</dt><dd>{displayValue(resource.resourceType, t('importPanel.unavailable'))}</dd></div>
            <div className="import-resource-emphasis"><dt>{t('importPanel.resourceSeason')}</dt><dd>{displayValue(resource.season, t('importPanel.unavailable'))}</dd></div>
            <div><dt>{t('importPanel.resourceUploaded')}</dt><dd>{formatUploadDate(resource.createdDate, t('importPanel.unavailable'))}</dd></div>
          </dl>
        </div>
      </Card>)}
    </div>
  </section>
}
