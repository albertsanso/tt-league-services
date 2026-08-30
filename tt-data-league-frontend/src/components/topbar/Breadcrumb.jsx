import { ChevronRight } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { getBreadcrumbItems } from '../../config/routes.js'
import { useTranslation } from 'react-i18next'

function Breadcrumb() {
  const location = useLocation()
  const { t } = useTranslation()
  const items = getBreadcrumbItems(location.pathname, location.search)

  return (
    <nav className="breadcrumb" aria-label={t('navigation.breadcrumb')}>
      <ol>
        {items.map((item, index) => {
          const isLast = index === items.length - 1

          return (
            <li key={item.label} className="breadcrumb-item">
              {isLast ? (
                <span className="breadcrumb-current" aria-current="page">{item.label}</span>
              ) : (
                <Link className="breadcrumb-link" to={item.path}>
                  {item.label}
                </Link>
              )}

              {isLast ? null : (
                <ChevronRight
                  size={14}
                  strokeWidth={1.5}
                  aria-hidden="true"
                  style={{ margin: '0 0.5rem', opacity: 0.4 }}
                />
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

export default Breadcrumb
