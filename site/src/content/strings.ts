/**
 * Every string on the site, per language. One file so a missing translation is a
 * type error rather than an English paragraph in the middle of a French page.
 */
export const locales = ['de', 'fr', 'it', 'en'] as const
export type Locale = (typeof locales)[number]

export const localeNames: Record<Locale, string> = {
  de: 'Deutsch',
  fr: 'Français',
  it: 'Italiano',
  en: 'English',
}

export type Nav = { home: string; selfHost: string; hosted: string; privacy: string }

export type Site = {
  htmlLang: string
  swissLabel: string
  nav: Nav
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
    honestTitle: string
    honestBody: string
    platformsTitle: string
    platformsBody: string
    androidLabel: string
    androidBody: string
    iosLabel: string
    iosBody: string
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
    betaTitle: string
    betaBody: string
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
  footer: { madeIn: string; source: string; contact: string }
}
