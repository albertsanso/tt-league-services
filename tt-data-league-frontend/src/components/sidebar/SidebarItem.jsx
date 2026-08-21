import { Link } from 'react-router-dom'
import Badge from '../ui/Badge.jsx'

function SidebarItem({ item, isActive, collapsed, onSelect }) {
  const Icon = item.icon
  const className = [
    'sidebar-item',
    isActive ? 'is-active' : '',
    collapsed ? 'is-collapsed' : '',
    item.disabled ? 'is-disabled' : '',
  ]
    .join(' ')
    .trim()

  const content = (
    <>
      <Icon strokeWidth={1.5} size={18} aria-hidden="true" />
      {collapsed ? null : <span className="sidebar-item-label">{item.label}</span>}
      {!collapsed && item.badge ? <Badge>{item.badge}</Badge> : null}
    </>
  )

  if (item.disabled) {
    return (
      <div className={className} aria-disabled="true" title={item.label}>
        {content}
      </div>
    )
  }

  return (
    <Link
      to={item.path}
      className={className}
      onClick={onSelect}
      title={collapsed ? item.label : undefined}
      aria-current={isActive ? 'page' : undefined}
      aria-label={collapsed ? item.label : undefined}
    >
      {content}
    </Link>
  )
}

export default SidebarItem
