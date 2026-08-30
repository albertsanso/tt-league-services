import { useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { routePaths } from '../config/routes.js'
import SettingsPanel from '../components/settings/SettingsPanel.jsx'
import ImportPanel from '../components/import/ImportPanel.jsx'

const destinationKeys = {
  [routePaths.administration]: 'administration',
  [routePaths.administrationUsers]: 'administrationUsers',
  [routePaths.administrationSettings]: 'administrationSettings',
  [routePaths.administrationImport]: 'administrationImport',
}

function AdministrationPage() {
  const { pathname } = useLocation()
  const { t } = useTranslation()
  const destination = destinationKeys[pathname] || 'administration'

  if (destination === 'administrationSettings') {
    return <SettingsPanel />
  }

  if (destination === 'administrationImport') {
    return <ImportPanel />
  }

  return (
    <section className="page-block" aria-labelledby="administration-title">
      <h1 id="administration-title" className="page-title">{t(`administration.${destination}.title`)}</h1>
      <p className="page-description">{t(`administration.${destination}.description`)}</p>
    </section>
  )
}

export default AdministrationPage
