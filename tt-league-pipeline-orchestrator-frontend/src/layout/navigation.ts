import CalendarMonthIcon from '@mui/icons-material/CalendarMonth'
import BarChartIcon from '@mui/icons-material/BarChart'
import PlaylistPlayIcon from '@mui/icons-material/PlaylistPlay'
import type { SvgIconComponent } from '@mui/icons-material'

export interface NavigationSection {
  readonly label: string
  readonly path: string
  readonly icon: SvgIconComponent
}

export const navigationSections: readonly NavigationSection[] = [
  { label: 'Calendar', path: '/calendar', icon: CalendarMonthIcon },
  { label: 'Runs', path: '/runs', icon: PlaylistPlayIcon },
  { label: 'Statistics', path: '/statistics', icon: BarChartIcon },
]
