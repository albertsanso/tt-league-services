import { useTranslation } from 'react-i18next'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'

const tone = (status) => ['COMPLETED', 'SUCCESS', 'DONE'].includes(status) ? 'success' : ['FAILED', 'ERROR'].includes(status) ? 'error' : status ? 'warning' : 'subtle'

export default function SeasonImportListItem({ season, onLoad, onSimulate }) {
  const { t } = useTranslation()
  const status = season.status ?? season.importStatus
  return <Card as="article" className="import-season-item">
    <div><strong>{season.season ?? season.id}</strong><small>{season.updatedAt ?? season.lastRun ?? t('importPanel.neverRun')}</small></div>
    <div className="import-season-actions">
      <Button variant="secondary" onClick={() => onLoad(season)}>{t('importPanel.load')}</Button><Badge tone={tone(status)}>{status ?? t('importPanel.ready')}</Badge>
      <Button variant="secondary" onClick={() => onSimulate(season)}>{t('importPanel.simulate')}</Button><Badge tone={tone(season.simulationStatus)}>{season.simulationStatus ?? t('importPanel.ready')}</Badge>
    </div>
  </Card>
}
