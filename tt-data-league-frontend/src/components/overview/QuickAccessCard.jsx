import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'

function QuickAccessCard({ item }) {
  const Icon = item.icon

  return (
    <Link to={item.path} className="quick-access-card card">
      <span className="quick-access-icon" aria-hidden="true">
        <Icon size={20} strokeWidth={1.5} />
      </span>
      <h3 className="quick-access-title">{item.title}</h3>
      <p className="quick-access-description">{item.description}</p>
      <span className="quick-access-footer">
        Explorar
        <ArrowRight className="quick-access-arrow" size={14} strokeWidth={1.5} />
      </span>
    </Link>
  )
}

export default QuickAccessCard
