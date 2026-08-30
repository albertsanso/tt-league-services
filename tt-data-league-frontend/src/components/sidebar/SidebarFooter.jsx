import { useTranslation } from 'react-i18next'

function SidebarFooter({ collapsed }) {
  const { t } = useTranslation()
  return (
    <div className="sidebar-footer">
      {collapsed ? (
        <p className="sidebar-footer-collapsed">v2.1.0</p>
      ) : (
        <div className="sidebar-footer-panel">
          <p>{t('shell.openProject')}</p>
          <p className="sidebar-version">v2.1.0</p>
        </div>
      )}
    </div>
  )
}

export default SidebarFooter
