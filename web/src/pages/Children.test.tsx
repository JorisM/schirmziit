import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { Children } from './Children'

const server = setupServer(
  http.get('/v1/children', () =>
    HttpResponse.json([{ id: 'c1', display_name: 'Kid One' }]),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderIsolated() {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <MemoryRouter>
        <Children />
      </MemoryRouter>
    </SWRConfig>,
  )
}

describe('Children', () => {
  it('links each child to their detail page', async () => {
    renderIsolated()
    const link = await screen.findByRole('link', { name: 'Kid One' })
    expect(link).toHaveAttribute('href', '/children/c1')
  })

  it('explains what to do when there are no children yet', async () => {
    server.use(http.get('/v1/children', () => HttpResponse.json([])))
    renderIsolated()
    expect(await screen.findByText(/no children yet/i)).toBeInTheDocument()
  })
})
