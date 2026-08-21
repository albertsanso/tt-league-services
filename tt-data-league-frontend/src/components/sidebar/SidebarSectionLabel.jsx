function SidebarSectionLabel({ collapsed, label }) {
  if (collapsed) {
    return null
  }

  return <p className="sidebar-section-label">{label}</p>
}

export default SidebarSectionLabel
