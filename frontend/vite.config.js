import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The browser only ever talks to the Java Spring Boot backend (rule 4 of the
// architecture) - the Python service is never exposed to the frontend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
