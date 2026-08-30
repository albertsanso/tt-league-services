import {
  BarChart3,
  Building2,
  LayoutDashboard,
  Shield,
  Settings,
  Swords,
  Upload,
  Users,
} from 'lucide-react'
import { routePaths } from './routes.js'

export const navigationSections = [
  {
    id: 'navegacio',
    labelKey: 'navigation.sectionNavigation',
    items: [
      {
        id: 'overview',
        labelKey: 'navigation.overview',
        path: routePaths.home,
        icon: LayoutDashboard,
      },
      {
        id: 'clubs',
        labelKey: 'navigation.clubs',
        path: routePaths.clubs,
        icon: Building2,
      },
      {
        id: 'jugadors',
        labelKey: 'navigation.players',
        path: routePaths.players(),
        icon: Users,
      },
      {
        id: 'partits',
        labelKey: 'navigation.matches',
        path: routePaths.matches(),
        icon: Swords,
      },
    ],
  },
  {
    id: 'analisi',
    labelKey: 'navigation.sectionAnalysis',
    items: [
      {
        id: 'analytics',
        labelKey: 'navigation.analytics',
        path: '/analytics',
        icon: BarChart3,
        disabled: true,
        badgeKey: 'common.soon',
      },
      {
        id: 'configuracio',
        labelKey: 'navigation.settings',
        path: '/settings',
        icon: Settings,
      },
    ],
  },
  {
    id: 'administracio',
    labelKey: 'navigation.sectionAdministration',
    items: [
      {
        id: 'administration',
        labelKey: 'navigation.administration',
        path: routePaths.administration,
        icon: Shield,
        role: 'ADMIN',
        ariaLabelKey: 'navigation.administrationToggle',
        children: [
          { id: 'administration-users', labelKey: 'navigation.administrationUsers', path: routePaths.administrationUsers, icon: Users },
          { id: 'administration-settings', labelKey: 'navigation.administrationSettings', path: routePaths.administrationSettings, icon: Settings },
          { id: 'administration-import', labelKey: 'navigation.administrationImport', path: routePaths.administrationImport, icon: Upload },
        ],
      },
    ],
  },
]
