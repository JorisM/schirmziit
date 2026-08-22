import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { Strings } from './types'
import { de } from './de'
import { en } from './en'
import { fr } from './fr'
import { it } from './it'

export const locales = { de, fr, it, en } as const
export type Locale = keyof typeof locales
export const localeOrder: Locale[] = ['de', 'fr', 'it', 'en']

const STORAGE_KEY = 'nestling.locale'

/**
 * Browser language decides, English is the fallback. A Swiss household can have
 * phones and laptops in three languages at once, so the choice is also
 * overridable and remembered per device.
 */
export function detectLocale(
  languages: readonly string[] = navigator.languages ?? [navigator.language],
  stored: string | null = safeRead(),
): Locale {
  if (stored && stored in locales) return stored as Locale
  for (const tag of languages) {
    const base = tag.slice(0, 2).toLowerCase()
    if (base in locales) return base as Locale
  }
  return 'en'
}

function safeRead(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    // Private windows and locked-down browsers throw rather than return null.
    return null
  }
}

type LocaleContextValue = {
  locale: Locale
  t: Strings
  setLocale: (next: Locale) => void
}

const LocaleContext = createContext<LocaleContextValue | null>(null)

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => detectLocale())

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Not being able to remember the choice is not worth an error.
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = locales[locale].meta.htmlLang
  }, [locale])

  const value = useMemo<LocaleContextValue>(
    () => ({ locale, t: locales[locale], setLocale }),
    [locale, setLocale],
  )

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
}

export function useI18n(): LocaleContextValue {
  const value = useContext(LocaleContext)
  if (!value) throw new Error('useI18n must be used inside <LocaleProvider>')
  return value
}

/** Durations read as "2 h 14 min" / "18 min" — never "0.23 h". */
export function formatDuration(ms: number, t: Strings): string {
  const minutes = Math.round(ms / 60_000)
  if (minutes < 60) return `${minutes} ${t.units.minutesShort}`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0
    ? `${hours} ${t.units.hoursShort}`
    : `${hours} ${t.units.hoursShort} ${rest} ${t.units.minutesShort}`
}
