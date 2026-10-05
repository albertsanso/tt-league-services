import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface State {
  readonly error: Error | null
}

/** Shows a recoverable error for a failed page render or lazy-chunk load. */
export class RouteErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Page failed to render', error, info.componentStack)
  }

  render() {
    if (this.state.error === null) {
      return this.props.children
    }
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={() => window.location.reload()}>
            Reload
          </Button>
        }
      >
        This page failed to load: {this.state.error.message}
      </Alert>
    )
  }
}
