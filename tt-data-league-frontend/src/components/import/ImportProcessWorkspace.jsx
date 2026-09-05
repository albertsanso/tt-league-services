import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import EmptyState from '../ui/EmptyState.jsx'
import ErrorState from '../ui/ErrorState.jsx'
import LoadingState from '../ui/LoadingState.jsx'

function badgeTone(status) {
  if (status === 'success') return 'success'
  if (status === 'failure') return 'error'
  if (status === 'empty-result') return 'warning'
  return 'subtle'
}

function resourceLabel(resource, t) {
  return [resource?.resourceType, resource?.season].filter(Boolean).join(' · ') || t('importPanel.resource')
}

function FindingList({ title, items, emptyText }) {
  if (!items.length) return <p>{emptyText}</p>
  return <>
    <h3>{title}</h3>
    <ul className="import-preview-list">
      {items.map((item, index) => (
        <li key={`${item.message}-${item.location}-${index}`}>
          <strong>{item.severity ? `${item.severity}: ` : null}</strong>
          {item.message}
          {item.location ? <small>{item.location}</small> : null}
        </li>
      ))}
    </ul>
  </>
}

export default function ImportProcessWorkspace({ resource, process, onRetry, onBackToResources }) {
  const { t } = useTranslation()
  const result = process.result
  const failed = result?.status === 'failure'
  const empty = result?.status === 'empty-result'
  const successful = result?.status === 'success'

  return <Card as="aside" className="import-report-panel import-preview-workspace" aria-live="polite">
    <h2>{t('importPanel.processTitle')}</h2>
    {!resource && !process.loading && !result && !process.error && (
      <EmptyState>{t('importPanel.processEmpty')}</EmptyState>
    )}
    {process.loading && <LoadingState>{t('importPanel.processLoading', { resource: resourceLabel(resource, t) })}</LoadingState>}
    {process.error && <ErrorState action={<Button variant="secondary" onClick={() => onRetry(resource)}>{t('importPanel.processRetry')}</Button>}>
      {t(process.error.status === 403 ? 'importPanel.forbidden' : 'importPanel.processFailure')}
    </ErrorState>}
    {result && !process.loading && !process.error && (
      <div className="import-preview-content">
        <div className="import-preview-heading">
          <strong>{resourceLabel(resource, t)}</strong>
          <Badge tone={badgeTone(result.status)}>{t(`importPanel.processStatus.${result.status}`)}</Badge>
        </div>
        {successful && <p>{t('importPanel.processSuccess', { count: result.itemsPersisted })}</p>}
        {empty && <p>{t('importPanel.processEmptyResult')}</p>}
        {failed && <p role="alert">{t('importPanel.processFailure')}</p>}
        <dl className="import-preview-summary">
          <div><dt>{t('importPanel.processFilesSeen')}</dt><dd>{result.filesSeen}</dd></div>
          <div><dt>{t('importPanel.processItemsPersisted')}</dt><dd>{result.itemsPersisted}</dd></div>
          <div><dt>{t('importPanel.processSkipped')}</dt><dd>{result.skipped}</dd></div>
        </dl>
        <FindingList title={t('importPanel.processFindings')} items={result.findings} emptyText={t('importPanel.processNoFindings')} />
        <FindingList title={t('importPanel.processErrors')} items={result.processingErrors} emptyText={t('importPanel.processNoErrors')} />
        <div className="import-preview-actions">
          {(failed || empty) && <Button variant="secondary" onClick={() => onRetry(resource)}>{t('importPanel.processRetry')}</Button>}
          {successful && <Button variant="secondary" onClick={onBackToResources}>{t('importPanel.processBackToResources')}</Button>}
        </div>
      </div>
    )}
  </Card>
}
