import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:1013',
        changeOrigin: true,
        secure: false,
        // Removed: the configure/proxyRes block was stripping the Secure flag,
        // but the cookie has NO Secure flag — so that block was a no-op.
        // Worse, in Vite 8 / http-proxy-middleware v3, modifying proxyRes.headers
        // in the proxyRes event can prevent Set-Cookie from reaching the browser.
        // Removing it lets Vite forward Set-Cookie headers correctly.
      },
      '/login': {
        target: 'http://localhost:1013',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})