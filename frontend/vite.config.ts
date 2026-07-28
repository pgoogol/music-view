import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Proxy dev/preview na backend Spring (M1.8) — front woła względne /api.
const apiProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: apiProxy },
  preview: { port: 5173, proxy: apiProxy },
  // testy komponentów i logiki frontu w jsdom (M3.1) — `npm test`
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
})
