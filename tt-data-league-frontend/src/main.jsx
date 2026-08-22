import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AppStateProvider } from './context/AppStateContext.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import './index.css'
import './app.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AppStateProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </AppStateProvider>
    </BrowserRouter>
  </StrictMode>,
)
