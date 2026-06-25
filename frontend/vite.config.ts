import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// Tailwind v3 runs via PostCSS (postcss.config.js), not a Vite plugin.
export default defineConfig({
  plugins: [react()],
})
