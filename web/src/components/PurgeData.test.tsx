import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PurgeData } from './PurgeData'
import { LocaleProvider, locales } from '../i18n'
import { api } from '../api/client'
import { clearErrorLog, fromProblem } from '../api/errors'

const purged = { deleted_usage_hours: 312, deleted_device_hours: 48, deleted_usage_days: 14 }

const renderPanel = (onPurged = vi.fn(async () => {})) => {
  render(
    <LocaleProvider>
      <PurgeData childId="kid" onPurged={onPurged} />
    </LocaleProvider>,
  )
  return onPurged
}

afterEach(() => {
  clearErrorLog()
  vi.restoreAllMocks()
})

describe('PurgeData', () => {
  it('deletes nothing on the first press', async () => {
    const del = vi.spyOn(api, 'del')
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.data.delete }))

    // The first press only asks. This is the one control on the page that
    // destroys a year of a child's history.
    expect(del).not.toHaveBeenCalled()
    expect(screen.getByText(locales.en.data.deleteBody)).toBeTruthy()
  })

  it('deletes on confirmation and reports what actually went', async () => {
    const del = vi.spyOn(api, 'del').mockResolvedValue(purged as never)
    const onPurged = renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.data.delete }))
    await userEvent.click(screen.getByRole('button', { name: locales.en.data.deleteConfirm }))

    await waitFor(() => expect(del).toHaveBeenCalledWith('/v1/children/kid/data'))
    // The server counts the rows it deleted; echoing them is what makes
    // "your data is gone" checkable rather than a claim.
    expect(screen.getByText('312')).toBeTruthy()
    expect(screen.getByText('48')).toBeTruthy()
    expect(screen.getByText('14')).toBeTruthy()
    // And the page has to re-read: the day on screen was just emptied.
    expect(onPurged).toHaveBeenCalled()
  })

  it('claims nothing when the delete fails', async () => {
    const onPurged = vi.fn(async () => {})
    vi.spyOn(api, 'del').mockRejectedValue(
      fromProblem(
        { type: 't', title: 't', status: 500, detail: 'internal error', code: 'SZ-E901', ref: '7f3a9c' },
        { endpoint: '/v1/children/kid/data', httpStatus: 500 },
      ),
    )
    renderPanel(onPurged)

    await userEvent.click(screen.getByRole('button', { name: locales.en.data.delete }))
    await userEvent.click(screen.getByRole('button', { name: locales.en.data.deleteConfirm }))

    await waitFor(() => expect(screen.getByText(/7f3a9c/)).toBeTruthy())
    // "Deleted." over a failed delete is the worst sentence this panel could
    // show: the parent stops looking for the data that is still there.
    expect(screen.queryByText(locales.en.data.deleted)).toBeNull()
    expect(onPurged).not.toHaveBeenCalled()
  })
})
