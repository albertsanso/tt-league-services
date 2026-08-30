import { Link } from 'react-router-dom'
import { ChevronDown, ChevronRight } from 'lucide-react'
import Badge from '../ui/Badge.jsx'

function SidebarItem({ item, isActive, collapsed, onSelect, expanded, onToggle }) {
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
      {!collapsed && item.children ? (
        expanded
          ? <ChevronDown size={16} strokeWidth={1.5} aria-hidden="true" />
          : <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
      ) : null}
    </>
  )

  if (item.disabled) {
    return (
      <div className={className} aria-disabled="true" title={item.label}>
        {content}
      </div>
    )
  }

  if (item.children) {
    return (
      <button
        type="button"
        className={className}
        onClick={onToggle}
        title={collapsed ? item.label : undefined}
        aria-label={item.ariaLabel || (collapsed ? item.label : undefined)}
        aria-expanded={expanded}
      >
        {content}
      </button>
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
