import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// vitest runs with `globals: false`, so Testing Library's automatic cleanup
// never registers itself. Without this, renders leak between tests and queries
// find duplicate elements.
afterEach(() => {
  cleanup()
})
