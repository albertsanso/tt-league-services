import { Building2, Swords, Users } from 'lucide-react'
import SectionLabel from '../ui/SectionLabel.jsx'
import QuickAccessCard from './QuickAccessCard.jsx'

const quickAccessItems = [
  {
    title: 'Cerca de clubs',
    description: 'Troba clubs, equips i resultats per categoria.',
    path: '/clubs',
    icon: Building2,
  },
  {
    title: 'Cerca de jugadors',
    description: 'Consulta rendiment i estadístiques dels jugadors.',
    path: '/jugadors',
    icon: Users,
  },
  {
    title: 'Cerca de partits',
    description: 'Busca partits per data, competició o jugador.',
    path: '/partits',
    icon: Swords,
  },
]

function QuickAccessGrid() {
  return (
    <section>
      <SectionLabel>Accés ràpid</SectionLabel>
      <div className="quick-access-grid">
        {quickAccessItems.map((item) => (
          <QuickAccessCard key={item.path} item={item} />
        ))}
      </div>
    </section>
  )
}

export default QuickAccessGrid
