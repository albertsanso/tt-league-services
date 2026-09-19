import { useTranslation } from 'react-i18next'

function SidebarFooter({ collapsed }) {
  const { t } = useTranslation()
  return (
    <div className="sidebar-footer">
      {collapsed ? (
        <p className="sidebar-footer-collapsed">v1.0.0-alpha.1</p>
      ) : (
        <div className="sidebar-footer-panel">
          <p>{t('shell.openProject')}</p>
          <p className="sidebar-version">v1.0.0-alpha.1</p>
        </div>
      )}
    </div>
  )
}

export default SidebarFooter
