function SidebarFooter({ collapsed }) {
  return (
    <div className="sidebar-footer">
      {collapsed ? (
        <p className="sidebar-footer-collapsed">v2.1.0</p>
      ) : (
        <div className="sidebar-footer-panel">
          <p>Projecte obert per a la comunitat del tennis de taula.</p>
          <p className="sidebar-version">v2.1.0</p>
        </div>
      )}
    </div>
  )
}

export default SidebarFooter
