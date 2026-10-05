import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    // MUI dialogs driven by userEvent exceed the 5 s default under parallel load.
    testTimeout: 20_000,
    setupFiles: './src/test/setup.ts',
  },
})
