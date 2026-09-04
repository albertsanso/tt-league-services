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
  if (!items.length) {
    return <p>{emptyText}</p>
  }
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

export default function ImportPreviewWorkspace({
  resource,
  preview,
  onRetry,
  onProceed,
}) {
  const { t } = useTranslation()
  const result = preview.result
  const failed = result?.status === 'failure'
  const empty = result?.status === 'empty-result'
  const successful = result?.status === 'success'

  return <Card as="aside" className="import-report-panel import-preview-workspace" aria-live="polite">
    <h2>{t('importPanel.previewTitle')}</h2>
    {!resource && !preview.loading && !result && !preview.error && (
      <EmptyState>{t('importPanel.previewEmpty')}</EmptyState>
    )}
    {preview.loading && <LoadingState>{t('importPanel.previewLoading', { resource: resourceLabel(resource, t) })}</LoadingState>}
    {preview.error && <ErrorState action={<Button variant="secondary" onClick={() => onRetry(resource)}>{t('importPanel.previewRetry')}</Button>}>
      {t(preview.error.status === 403 ? 'importPanel.forbidden' : 'importPanel.previewFailure')}
    </ErrorState>}
    {result && !preview.loading && !preview.error && (
      <div className="import-preview-content">
        <div className="import-preview-heading">
          <strong>{resourceLabel(resource, t)}</strong>
          <Badge tone={badgeTone(result.status)}>{t(`importPanel.previewStatus.${result.status}`)}</Badge>
        </div>
        {successful && <p>{t('importPanel.previewSuccess', { count: result.itemsDispatched })}</p>}
        {empty && <p>{t('importPanel.previewEmptyResult')}</p>}
        {failed && <p role="alert">{t('importPanel.previewFailure')}</p>}
        <dl className="import-preview-summary">
          <div><dt>{t('importPanel.previewFilesSeen')}</dt><dd>{result.filesSeen}</dd></div>
          <div><dt>{t('importPanel.previewItemsValidated')}</dt><dd>{result.itemsDispatched}</dd></div>
          <div><dt>{t('importPanel.previewSkipped')}</dt><dd>{result.skipped}</dd></div>
        </dl>
        <FindingList
          title={t('importPanel.previewFindings')}
          items={result.validationFindings}
          emptyText={t('importPanel.previewNoFindings')}
        />
        <FindingList
          title={t('importPanel.previewErrors')}
          items={result.processingErrors}
          emptyText={t('importPanel.previewNoErrors')}
        />
        <div className="import-preview-actions">
          {(failed || empty) && <Button variant="secondary" onClick={() => onRetry(resource)}>{t('importPanel.previewRetry')}</Button>}
          {successful && (
            <Button variant="primary" onClick={() => onProceed(resource)} disabled={preview.importing}>
              {preview.importing ? t('importPanel.previewProceeding') : t('importPanel.previewProceed')}
            </Button>
          )}
        </div>
        {preview.importError && <p className="import-preview-error" role="alert">{t('importPanel.actionError')}</p>}
        {preview.importResult && <p className="import-preview-success" role="status">{t('importPanel.actionSuccess')}</p>}
      </div>
    )}
  </Card>
}
