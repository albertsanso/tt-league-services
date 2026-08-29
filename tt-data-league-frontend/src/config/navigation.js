import {
  BarChart3,
  Building2,
  LayoutDashboard,
  Settings,
  Swords,
  Users,
} from 'lucide-react'
import { routePaths } from './routes.js'

export const navigationSections = [
  {
    id: 'navegacio',
    label: 'Navegació',
    items: [
      {
        id: 'overview',
        label: 'Overview',
        path: routePaths.home,
        icon: LayoutDashboard,
      },
      {
        id: 'clubs',
        label: 'Cerca de clubs',
        path: routePaths.clubs,
        icon: Building2,
      },
      {
        id: 'jugadors',
        label: 'Cerca de jugadors',
        path: routePaths.players(),
        icon: Users,
      },
      {
        id: 'partits',
        label: 'Cerca de partits',
        path: '/partits',
        icon: Swords,
      },
    ],
  },
  {
    id: 'analisi',
    label: 'Anàlisi',
    items: [
      {
        id: 'analytics',
        label: 'Analytics',
        path: '/analytics',
        icon: BarChart3,
        disabled: true,
        badge: 'Aviat',
      },
      {
        id: 'configuracio',
        label: 'Configuració',
        path: '/settings',
        icon: Settings,
      },
    ],
  },
]
