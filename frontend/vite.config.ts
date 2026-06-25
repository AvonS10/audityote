import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// Tailwind v3 runs via PostCSS (postcss.config.js), not a Vite plugin.
// The dev server proxies /api to the Spring Boot backend so the SPA is same-origin with it —
// session cookies + CSRF work with no CORS, mirroring the production reverse-proxy setup.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
