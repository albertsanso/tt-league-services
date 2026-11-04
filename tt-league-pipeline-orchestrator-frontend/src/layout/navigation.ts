import CalendarMonthIcon from '@mui/icons-material/CalendarMonth'
import BarChartIcon from '@mui/icons-material/BarChart'
import PlaylistPlayIcon from '@mui/icons-material/PlaylistPlay'
import ScheduleIcon from '@mui/icons-material/Schedule'
import type { SvgIconComponent } from '@mui/icons-material'

export interface NavigationSection {
  readonly label: string
  readonly path: string
  readonly icon: SvgIconComponent
}

export const navigationSections: readonly NavigationSection[] = [
  { label: 'Calendar', path: '/calendar', icon: CalendarMonthIcon },
  { label: 'Runs', path: '/runs', icon: PlaylistPlayIcon },
  { label: 'Polling', path: '/polling', icon: ScheduleIcon },
  { label: 'Statistics', path: '/statistics', icon: BarChartIcon },
]
