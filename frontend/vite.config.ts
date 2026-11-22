import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { syntheticVisualPreview } from './visual-preview/serve.ts'

// https://vite.dev/config/
export default defineConfig({
  base: '/agap/',
  plugins: [react(), tailwindcss(), syntheticVisualPreview()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  build: {
    chunkSizeWarningLimit: 600,
  },
})
