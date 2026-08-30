import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AppStateProvider } from './context/AppStateContext.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import i18n from './i18n/index.js'
import { I18nextProvider } from 'react-i18next'
import './index.css'
import './app.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>
        <AppStateProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </AppStateProvider>
      </I18nextProvider>
    </BrowserRouter>
  </StrictMode>,
)
