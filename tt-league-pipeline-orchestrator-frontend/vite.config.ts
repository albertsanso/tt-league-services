import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const orchestratorTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8095'
  const platformTarget = env.VITE_PLATFORM_PROXY_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api/pipeline': {
          target: orchestratorTarget,
          changeOrigin: true,
        },
        '/api/v1': {
          target: platformTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
