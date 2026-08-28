/**
 * Every string on the site, per language. One file so a missing translation is a
 * type error rather than an English paragraph in the middle of a French page.
 */
import type { MatrixColumn, MatrixRowKey, MatrixStatus } from './matrix'

export const locales = ['de', 'fr', 'it', 'en'] as const
export type Locale = (typeof locales)[number]

export const localeNames: Record<Locale, string> = {
  de: 'Deutsch',
  fr: 'Français',
  it: 'Italiano',
  en: 'English',
}

export type Nav = { home: string; selfHost: string; hosted: string; privacy: string }

/** The four pages, in nav order. One key for the nav, the meta copy and hreflang. */
export type PageKey = 'home' | 'selfHost' | 'hosted' | 'privacy'

/**
 * What a search result and a chat preview show. Deliberately not the page's own
 * headline: `home.lead` is a paragraph written to sit under an h1 and runs 180
 * to 230 characters, where a description is cut at about 155, and an h1 that
 * reads well on the page is longer than a title that survives a result list.
 * Writing both from one string means one of the two is always wrong.
 */
export type Meta = { title: string; description: string }

/**
 * The private-alpha notice and the waiting list it points at. Every page carries
 * the banner: someone deep-linked to the hosted page would otherwise read a
 * finished offer.
 */
export type Alpha = {
  bannerTitle: string
  bannerBody: string
  bannerCta: string
  title: string
  lead: string
  emailLabel: string
  placeholder: string
  submit: string
  sending: string
  done: string
  invalid: string
  failed: string
  stored: string
  /** Shown instead of the form with JavaScript off, and in a fork with no API configured. */
  mailFallback: string
  mailCta: string
}

/**
 * The words around `matrix.ts`. The statuses are not in here on purpose: a
 * `Record<MatrixRowKey, …>` makes a forgotten row a type error in that one
 * language, while a per-language status would let the four pages disagree about
 * what the product does.
 */
export type Matrix = {
  title: string
  lead: string
  featureHeader: string
  columns: Record<MatrixColumn, string>
  groups: { measure: string; view: string }
  legend: Record<MatrixStatus, string>
  /** Read out for a column that is not meant to do this row at all. */
  notApplicable: string
  rows: Record<MatrixRowKey, { label: string; note?: string }>
}

export type Site = {
  htmlLang: string
  /** `og:locale`, which wants language_TERRITORY where `htmlLang` wants a hyphen. */
  ogLocale: string
  meta: Record<PageKey, Meta>
  swissLabel: string
  nav: Nav
  alpha: Alpha
  home: {
    kicker: string
    title: string
    lead: string
    ctaSelfHost: string
    ctaHosted: string
    measuresTitle: string
    measures: string[]
    neverTitle: string
    never: string[]
    howTitle: string
    how: string[]
    ribbonTitle: string
    ribbonBody: string
    childSeesTitle: string
    childSeesBody: string
    platformsTitle: string
    platformsBody: string
    androidLabel: string
    androidBody: string
    iosLabel: string
    iosBody: string
    matrix: Matrix
    openTitle: string
    openBody: string
  }
  choose: {
    title: string
    selfHostTitle: string
    selfHostFor: string
    selfHostPoints: string[]
    hostedTitle: string
    hostedFor: string
    hostedPoints: string[]
  }
  selfHost: {
    title: string
    lead: string
    needTitle: string
    need: string[]
    stepsTitle: string
    proxyTitle: string
    proxyBody: string
    firstUserTitle: string
    firstUserBody: string
    pairTitle: string
    pairBody: string
    backupTitle: string
    backupBody: string
    upgradeTitle: string
    upgradeBody: string
    troubleTitle: string
    trouble: { problem: string; fix: string }[]
  }
  hosted: {
    title: string
    lead: string
    whereTitle: string
    whereBody: string
    scaleTitle: string
    scaleBody: string
    priceTitle: string
    priceBody: string
    joinTitle: string
    joinBody: string
    joinCta: string
  }
  privacy: {
    title: string
    lead: string
    sections: { title: string; body: string }[]
    analyticsTitle: string
    analyticsBody: string
  }
  resources: {
    title: string
    lead: string
    items: { name: string; note: string; href: string }[]
  }

  footer: { madeIn: string; source: string; contact: string }
}
