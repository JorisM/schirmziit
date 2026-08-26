import { Component, type ReactNode } from 'react'
import { AppError, unexpected } from '../api/errors'
import { ErrorPanel } from './ErrorPanel'

/**
 * A bug in the dashboard is still an error a parent should be able to report.
 * Without this, a render crash is a white page: nothing to read, nothing to
 * screenshot, nothing to say beyond "it broke".
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { error: AppError | null }> {
  state: { error: AppError | null } = { error: null }

  static getDerivedStateFromError(cause: unknown) {
    return { error: unexpected(cause) }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto max-w-md p-6">
          <ErrorPanel error={this.state.error} onRetry={() => window.location.reload()} />
        </div>
      )
    }
    return this.props.children
  }
}

/**
 * The two failures React never sees: an async rejection nobody caught, and an
 * error thrown outside a component. Recorded rather than shown — the page is
 * still usable — so that the next visible error's copy block carries them.
 */
export function installGlobalErrorHandlers(target: Window = window) {
  target.addEventListener('unhandledrejection', (event) => {
    unexpected(event.reason)
  })
  target.addEventListener('error', (event) => {
    unexpected(event.error)
  })
}
