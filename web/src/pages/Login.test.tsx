import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { Login } from './Login'

const server = setupServer(
  http.post('/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string }
    if (body.password === 'correct horse battery staple') {
      return HttpResponse.json({ ok: true })
    }
    return HttpResponse.json(
      {
        type: 'https://nestling.dev/problems/invalid-credentials',
        title: 'invalid-credentials',
        status: 401,
        detail: 'invalid credentials',
      },
      { status: 401 },
    )
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('Login', () => {
  it('shows the API problem detail when credentials are wrong', async () => {
    render(<Login onSignedIn={() => {}} />)
    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('invalid credentials')
  })

  it('calls onSignedIn after a successful login', async () => {
    let signedIn = false
    render(
      <Login
        onSignedIn={() => {
          signedIn = true
        }}
      />,
    )
    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await screen.findByText('Signed in')
    expect(signedIn).toBe(true)
  })
})
