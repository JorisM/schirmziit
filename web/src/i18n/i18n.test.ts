import { describe, expect, it } from 'vitest'
import { detectLocale, formatDuration, localeOrder, locales } from './index'
import { en } from './en'
import type { Strings } from './types'

/** Every leaf path in the reference locale. */
function paths(value: unknown, prefix = ''): string[] {
  if (Array.isArray(value)) return [prefix]
  if (value && typeof value === 'object') {
    return Object.entries(value).flatMap(([key, inner]) =>
      paths(inner, prefix ? `${prefix}.${key}` : key),
    )
  }
  return [prefix]
}

function read(source: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, key) => (acc as Record<string, unknown>)?.[key], source)
}

describe('translations', () => {
  const reference = paths(en)

  it.each(localeOrder)('%s has every string, none empty', (locale) => {
    const strings = locales[locale] as Strings
    for (const path of reference) {
      const value = read(strings, path)
      expect(value, `${locale} is missing ${path}`).toBeDefined()
      if (typeof value === 'string') {
        expect(value.trim(), `${locale}.${path} is empty`).not.toBe('')
      }
    }
  })

  it.each(localeOrder)('%s keeps list lengths in step with English', (locale) => {
    const strings = locales[locale] as Strings
    // A short list reads as a missing promise: "what we do not collect" with
    // four bullets in German and five in English is a translation bug, not style.
    expect(strings.help.measures.length).toBe(en.help.measures.length)
    expect(strings.help.notCollected.length).toBe(en.help.notCollected.length)
    expect(strings.help.howSteps.length).toBe(en.help.howSteps.length)
    expect(strings.help.resources.length).toBe(en.help.resources.length)
  })

  it.each(localeOrder)('%s links help resources by https, with a name and a note', (locale) => {
    const strings = locales[locale] as Strings
    for (const resource of strings.help.resources) {
      expect(resource.href, `${locale} resource link`).toMatch(/^https:\/\//)
      expect(resource.name.trim()).not.toBe('')
      expect(resource.note.trim()).not.toBe('')
    }
  })

  it.each(localeOrder)('%s frames monitoring as protection, never as something covert', (locale) => {
    // The product promise: a parent watches to protect a child, and the child is
    // told. Copy that hints at hidden monitoring undoes that in one sentence.
    const flat = JSON.stringify(locales[locale])
    expect(flat).not.toMatch(/heimlich|sneak|en cachette|di nascosto|in segreto|in secret/i)
  })

  it('German uses Swiss spelling', () => {
    const flat = JSON.stringify(locales.de)
    expect(flat, 'Schweizer Hochdeutsch has no ß').not.toMatch(/ß/)
  })

  it('German addresses the reader informally', () => {
    const flat = JSON.stringify(locales.de)
    expect(flat).toMatch(/\bdu\b|\bdein/i)
    // Only formal ADDRESS is wrong here. Plain "sie/Sie" is a normal pronoun for
    // feminine nouns ("die App … sie liest"), so match the Höflichkeitsform's
    // own markers instead of banning the word.
    expect(flat, 'no formal Sie-form leftovers').not.toMatch(
      /\bIhre[rmns]?\b|\bIhnen\b|\bSie (k\u00f6nnen|sehen|m\u00fcssen|haben|finden|erhalten)\b/,
    )
  })
})

describe('detectLocale', () => {
  it('prefers a stored choice over the browser', () => {
    expect(detectLocale(['fr-CH'], 'it')).toBe('it')
  })

  it('matches a regional tag to its base language', () => {
    expect(detectLocale(['de-CH', 'en-US'], null)).toBe('de')
  })

  it('walks the whole preference list', () => {
    expect(detectLocale(['pt-BR', 'it-IT'], null)).toBe('it')
  })

  it('falls back to English for a language we do not have', () => {
    expect(detectLocale(['ja-JP'], null)).toBe('en')
  })

  it('ignores a stored value that is not a locale', () => {
    expect(detectLocale(['fr'], 'klingon')).toBe('fr')
  })
})

describe('formatDuration', () => {
  it('reads like a person wrote it', () => {
    expect(formatDuration(600_000, en)).toBe('10 min')
    expect(formatDuration(3_600_000, en)).toBe('1 h')
    expect(formatDuration(8_040_000, en)).toBe('2 h 14 min')
  })
})
