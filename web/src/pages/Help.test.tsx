import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Help } from './Help'
import { LocaleProvider, locales } from '../i18n'

describe('Help', () => {
  it('puts what is collected next to what is not', () => {
    render(
      <LocaleProvider>
        <Help />
      </LocaleProvider>,
    )
    expect(screen.getByText(locales.en.help.measuresTitle)).toBeInTheDocument()
    expect(screen.getByText(locales.en.help.notCollectedTitle)).toBeInTheDocument()
  })

  it('states plainly that nothing is blocked', () => {
    render(
      <LocaleProvider>
        <Help />
      </LocaleProvider>,
    )
    expect(screen.getByText(locales.en.help.notAControl)).toBeInTheDocument()
  })

  it('lists every step and every limit from the locale', () => {
    render(
      <LocaleProvider>
        <Help />
      </LocaleProvider>,
    )
    for (const line of locales.en.help.notCollected) {
      expect(screen.getByText(line)).toBeInTheDocument()
    }
    for (const step of locales.en.help.howSteps) {
      expect(screen.getByText(step)).toBeInTheDocument()
    }
  })
})
