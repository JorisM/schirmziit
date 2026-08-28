import type { Locale, Site } from './strings'
import { de } from './de'
import { en } from './en'
import { fr } from './fr'
import { it } from './it'

export const sites: Record<Locale, Site> = { de, fr, it, en }
export { locales, localeNames } from './strings'
export type { Locale, Meta, PageKey, Site } from './strings'

/** '' for the default locale, '/fr' etc. for the others. */
export function prefix(locale: Locale): string {
  return locale === 'de' ? '' : `/${locale}`
}
