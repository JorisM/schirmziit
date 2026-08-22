import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Dev is same-origin, matching production where the Rust binary serves both.
    // 127.0.0.1, not localhost: Node resolves localhost to ::1 first, and the
    // server binds IPv4 only, so a localhost target fails with ECONNREFUSED.
    proxy: { '/v1': 'http://127.0.0.1:8099', '/healthz': 'http://127.0.0.1:8099' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: false,
  },
})
