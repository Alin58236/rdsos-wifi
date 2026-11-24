import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend listens on 127.0.0.1:8080. The dev server forwards /api (REST) and /ws (WebSocket) to it,
// so the browser only ever talks to http://localhost:5173.
const BACKEND_URL = 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': BACKEND_URL,
      '/ws': { target: BACKEND_URL, ws: true },
    },
  },
})
