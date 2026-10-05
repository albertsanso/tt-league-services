import MenuIcon from '@mui/icons-material/Menu'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Toolbar from '@mui/material/Toolbar'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { Suspense, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useEventConnection } from '../events/useRunEvents'
import type { ConnectionState } from '../events/eventsContext'
import { DRAWER_WIDTH } from '../theme'
import { navigationSections } from './navigation'
import { RouteErrorBoundary } from './RouteErrorBoundary'

const CONNECTION_LABELS: Record<ConnectionState, { label: string; color: 'success' | 'warning' | 'default' | 'error'; hint: string }> = {
  connecting: { label: 'Connecting', color: 'default', hint: 'Opening the live event stream' },
  open: { label: 'Live', color: 'success', hint: 'Receiving live updates' },
  reconnecting: { label: 'Reconnecting', color: 'warning', hint: 'Live updates interrupted; retrying' },
  stopped: { label: 'Offline', color: 'error', hint: 'Live updates stopped' },
}

function EventIndicator() {
  const { state } = useEventConnection()
  const { label, color, hint } = CONNECTION_LABELS[state]
  return (
    <Tooltip title={hint}>
      <Chip size="small" color={color} label={`Events: ${label}`} sx={{ mr: 2 }} />
    </Tooltip>
  )
}

export default function AppLayout() {
  const { user, signOut } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)

  const navigation = (
    <Box component="nav" aria-label="Sections">
      <Toolbar />
      <List>
        {navigationSections.map(({ label, path, icon: Icon }) => (
          <ListItemButton
            key={path}
            component={NavLink}
            to={path}
            onClick={() => setMobileOpen(false)}
            sx={{ '&.active': { bgcolor: 'action.selected' } }}
          >
            <ListItemIcon>
              <Icon />
            </ListItemIcon>
            <ListItemText primary={label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            aria-label="Open navigation"
            onClick={() => setMobileOpen(true)}
            sx={{ mr: 1, display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }}>
            Pipeline control centre
          </Typography>
          <EventIndicator />
          <Typography variant="body2" sx={{ mr: 2 }}>
            {user?.username}
          </Typography>
          <Button color="inherit" onClick={() => signOut('user')}>
            Sign out
          </Button>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{ display: { xs: 'block', md: 'none' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
      >
        {navigation}
      </Drawer>
      <Drawer
        variant="permanent"
        open
        sx={{
          display: { xs: 'none', md: 'block' },
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        {navigation}
      </Drawer>
      <Box component="main" sx={{ flex: 1, minWidth: 0, p: 3 }}>
        <Toolbar />
        <RouteErrorBoundary>
          <Suspense
            fallback={
              <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}>
                <CircularProgress aria-label="Loading page" />
              </Box>
            }
          >
            <Outlet />
          </Suspense>
        </RouteErrorBoundary>
      </Box>
    </Box>
  )
}
