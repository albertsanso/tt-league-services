import { useTranslation } from 'react-i18next'
import Card from '../ui/Card.jsx'
import EmptyState from '../ui/EmptyState.jsx'
import ErrorState from '../ui/ErrorState.jsx'

export default function ImportReportPanel({ season, job }) {
  const { t } = useTranslation()
  return <Card as="aside" className="import-report-panel" aria-live="polite">
    <h2>{t('importPanel.reportTitle')}</h2>
    {!season && !job && <EmptyState>{t('importPanel.reportEmpty')}</EmptyState>}
    {job?.error && <ErrorState>{t(job.error.status === 403 ? 'importPanel.forbidden' : 'importPanel.actionError')}</ErrorState>}
    {job && !job.error && <div className="import-report-content"><strong>{season?.season ?? t('importPanel.fileReport')}</strong><p>{job.message ?? job.status ?? t('importPanel.actionSuccess')}</p></div>}
  </Card>
}
