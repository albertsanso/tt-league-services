import { useMemo } from 'react'
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

function isImported(resource) {
  return resource.status?.toUpperCase() === 'PROCESSED'
}

function isProcessing(resource) {
  return resource.status?.toUpperCase() === 'PROCESSING'
}

const bySeasonDesc = (a, b) =>
  String(b.season ?? '').localeCompare(String(a.season ?? ''), undefined, { numeric: true })

function ImportResourceGroup({ titleKey, resources, onSimulate, onImport, disabled, t }) {
  if (resources.length === 0) return null

  return <div className="import-resource-group">
    <h3>{t(titleKey)}</h3>
    <div className="import-resource-items" role="list">
      {resources.map((resource) => <Card as="article" className="import-resource-item" key={resource.id} role="listitem">
        <div className="import-resource-actions">
          <Button variant="secondary" onClick={() => onSimulate(resource)} disabled={disabled}>{t('importPanel.simulate')}</Button>
          <Button variant="primary" onClick={() => onImport(resource)} disabled={disabled}>{t('importPanel.import')}</Button>
        </div>
        <div className="import-resource-content">
          <div className="import-resource-heading">
            <Badge tone={tone(resource.status)}>{displayValue(resource.status, t('importPanel.resourceReady'))}</Badge>
            {isProcessing(resource) && <span
              className="import-resource-processing-indicator"
              role="status"
              aria-live="polite"
            >
              <span aria-hidden="true" />
              <span className="visually-hidden">{t('importPanel.resourceProcessing')}</span>
            </span>}
          </div>
          <dl className="import-resource-details">
            <div className="import-resource-emphasis"><dt>{t('importPanel.resourceType')}</dt><dd>{displayValue(resource.resourceType, t('importPanel.unavailable'))}</dd></div>
            <div className="import-resource-emphasis"><dt>{t('importPanel.resourceSeason')}</dt><dd>{displayValue(resource.season, t('importPanel.unavailable'))}</dd></div>
            <div><dt>{t('importPanel.resourceUploaded')}</dt><dd>{formatUploadDate(resource.createdDate, t('importPanel.unavailable'))}</dd></div>
          </dl>
        </div>
      </Card>)}
    </div>
  </div>
}

export default function ImportResourceList({ resources, onSimulate, onImport, disabled = false }) {
  const { t } = useTranslation()

  const { imported, pending } = useMemo(() => {
    const importedResources = resources.filter(isImported).sort(bySeasonDesc)
    const pendingResources = resources.filter((resource) => !isImported(resource)).sort(bySeasonDesc)
    return { imported: importedResources, pending: pendingResources }
  }, [resources])

  return <section className="import-resource-list" aria-labelledby="import-resources-title">
    <h2 id="import-resources-title">{t('importPanel.resourcesTitle')}</h2>
    <ImportResourceGroup
      titleKey="importPanel.resourcesImportedTitle"
      resources={imported}
      onSimulate={onSimulate}
      onImport={onImport}
      disabled={disabled}
      t={t}
    />
    <ImportResourceGroup
      titleKey="importPanel.resourcesPendingTitle"
      resources={pending}
      onSimulate={onSimulate}
      onImport={onImport}
      disabled={disabled}
      t={t}
    />
  </section>
}
