import { useTranslation } from 'react-i18next'
import SeasonImportListItem from './SeasonImportListItem.jsx'

export default function SeasonImportList({ seasons, onLoad, onSimulate }) {
  const { t } = useTranslation()
  return <section className="import-season-list" aria-labelledby="import-seasons-title">
    <h2 id="import-seasons-title">{t('importPanel.seasonsTitle')}</h2>
    {seasons.map((season) => <SeasonImportListItem key={season.id} season={season} onLoad={onLoad} onSimulate={onSimulate} />)}
  </section>
}
