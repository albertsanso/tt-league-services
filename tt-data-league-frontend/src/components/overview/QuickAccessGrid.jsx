import { Building2, Swords, Users } from 'lucide-react'
import SectionLabel from '../ui/SectionLabel.jsx'
import QuickAccessCard from './QuickAccessCard.jsx'
import { useTranslation } from 'react-i18next'

const quickAccessItems = [
  {
    title: 'navigation.clubs',
    description: 'overview.findClubs',
    path: '/clubs',
    icon: Building2,
  },
  {
    title: 'navigation.players',
    description: 'overview.findPlayers',
    path: '/jugadors',
    icon: Users,
  },
  {
    title: 'navigation.matches',
    description: 'overview.findMatches',
    path: '/partits',
    icon: Swords,
  },
]

function QuickAccessGrid() {
  const { t } = useTranslation()
  return (
    <section>
      <SectionLabel>{t('overview.quickAccess')}</SectionLabel>
      <div className="quick-access-grid">
        {quickAccessItems.map((item) => (
          <QuickAccessCard key={item.path} item={{ ...item, title: t(item.title), description: t(item.description) }} />
        ))}
      </div>
    </section>
  )
}

export default QuickAccessGrid
