import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { safeRedirectPath } from './safeRedirectPath'

export default function LoginPage() {
  const { status, signIn, signOutReason, notice } = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (status === 'signed-in') {
    return <Navigate to={safeRedirectPath((location.state as { from?: unknown } | null)?.from)} replace />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      await signIn(username, password)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Sign-in failed')
      setPending(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', p: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 400 }}>
        <CardContent>
          <Box component="form" onSubmit={submit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="h5" component="h1">
              Pipeline control centre
            </Typography>
            {signOutReason === 'expired' && <Alert severity="info">Your session has expired. Sign in again.</Alert>}
            {notice !== undefined && <Alert severity="warning">{notice}</Alert>}
            {error !== null && <Alert severity="error">{error}</Alert>}
            <TextField
              label="Username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              required
              autoFocus
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
            <Button type="submit" variant="contained" disabled={pending}>
              Sign in
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
