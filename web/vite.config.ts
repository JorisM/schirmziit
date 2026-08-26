import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Read rather than imported: an import attribute (`with { type: 'json' }`) is
// still uneven across Node versions, and a config that fails to parse takes the
// whole build with it.
const { version } = JSON.parse(
  readFileSync(new URL('./package.json', import.meta.url), 'utf8'),
) as { version: string }

export default defineConfig({
  define: {
    // The version a parent reads out of the copy-details block. Without it the
    // report says "dev" and nobody can tell which build broke.
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
  },
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
